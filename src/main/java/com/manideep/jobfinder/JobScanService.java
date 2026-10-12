package com.manideep.jobfinder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobScanService {
    private final List<JobSourceAdapter> adapters;
    private final JobPostingRepository repository;
    private final JobScoringService scoring;
    private final ContactResearchService contacts;
    private final EmailReportService emailReport;
    private final SourceHealthTracker tracker;
    private final SourceConfig config;
    private volatile ScanStatus lastStatus = new ScanStatus("NOT_RUN", null, 0, 0, List.of(),
            "Run scan to discover jobs.");

    public JobScanService(List<JobSourceAdapter> adapters, JobPostingRepository repository, JobScoringService scoring,
            ContactResearchService contacts, EmailReportService emailReport, SourceHealthTracker tracker,
            SourceConfig config) {
        this.adapters = adapters;
        this.repository = repository;
        this.scoring = scoring;
        this.contacts = contacts;
        this.emailReport = emailReport;
        this.tracker = tracker;
        this.config = config;
    }

    public synchronized ScanStatus runScan(boolean sendEmail) {
        Instant started = Instant.now();
        lastStatus = new ScanStatus("RUNNING", started, 0, 0, List.of(),
                "Scan in progress across all career boards...");
        int found = 0, eligible = 0;
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        List<JobCandidate> allCandidates = Collections.synchronizedList(new ArrayList<>());
        List<ReportJob> reportJobs = new ArrayList<>();

        // 1. Fetch concurrently from all adapters using Java 21 Virtual Threads
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (JobSourceAdapter adapter : adapters) {
                futures.add(executor.submit(() -> {
                    try {
                        List<JobCandidate> candidates = adapter.fetch();
                        if (candidates != null) {
                            allCandidates.addAll(candidates);
                        }
                    } catch (Exception ex) {
                        errors.add(
                                adapter.sourceName() + ": " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    }
                }));
            }
            for (var f : futures) {
                try {
                    f.get();
                } catch (Exception ex) {
                    errors.add("Fetch error: " + ex.getMessage());
                }
            }
        }

        // Collect critical board failures from tracker
        for (var issue : tracker.getIssues()) {
            if ("FAILED".equalsIgnoreCase(issue.status())) {
                errors.add(issue.company() + " (" + issue.source() + "): " + issue.message());
            }
        }

        // 2. Deduplicate candidates in case of pagination overlap
        Map<String, JobCandidate> uniqueCandidates = new LinkedHashMap<>();
        for (JobCandidate c : allCandidates) {
            if (c != null && c.externalId() != null && !c.externalId().isBlank()) {
                uniqueCandidates.putIfAbsent(c.source() + ":" + c.externalId(), c);
            }
        }

        // 3. Score, filter, and persist (store only jobs >= minScore)
        int minScoreThreshold = config != null ? config.getMinScore() : 59;
        for (JobCandidate candidate : uniqueCandidates.values()) {
            found++;
            Optional<JobPosting> existing = repository.findBySourceAndExternalId(candidate.source(),
                    candidate.externalId());
            boolean isNew = existing.isEmpty();
            JobScoringService.Result r = scoring.score(candidate, isNew);
            if (r.score() < minScoreThreshold || !r.eligible()) {
                continue; // Do not store jobs outside India, ineligible roles, or below min score
            }
            Instant now = Instant.now();
            JobPosting entity;
            if (existing.isPresent()) {
                entity = existing.get();
                entity.refresh(candidate.title(), candidate.location(), candidate.jobUrl(), candidate.description(),
                        candidate.publishedAt(), now, r.score(), r.compensationStatus(), candidate.compensationText(),
                        r.eligible(), isNew);
            } else {
                entity = new JobPosting(candidate.source(), candidate.externalId(), candidate.company(),
                        candidate.title(), candidate.location(),
                        candidate.jobUrl(), candidate.description(), candidate.publishedAt(), now, r.score(),
                        r.compensationStatus(),
                        candidate.compensationText(), r.eligible(), true);
            }
            try {
                repository.save(entity);
                if (r.eligible()) {
                    eligible++;
                    if (isNew) {
                        reportJobs.add(new ReportJob(candidate, r, contacts.links(candidate)));
                    }
                }
            } catch (Exception saveEx) {
                errors.add("Database Save (" + entity.getCompany() + "): " + saveEx.getClass().getSimpleName() + ": "
                        + saveEx.getMessage());
            }
        }

        // Sort new qualifying jobs descending by match score
        reportJobs.sort((a, b) -> {
            int scoreCmp = Integer.compare(b.result().score(), a.result().score());
            if (scoreCmp != 0) return scoreCmp;
            return a.candidate().company().compareToIgnoreCase(b.candidate().company());
        });

        String emailStatus = "NOT_REQUESTED";
        if (sendEmail) {
            try {
                emailReport.send(reportJobs, errors, started);
                emailStatus = "SENT (" + reportJobs.size() + " new)";
            } catch (Exception ex) {
                emailStatus = "FAILED: " + ex.getMessage();
            }
        }
        lastStatus = new ScanStatus("COMPLETED", started, found, eligible, errors, emailStatus);
        return lastStatus;
    }

    public ScanStatus status() {
        return lastStatus;
    }

    public SourceHealthTracker tracker() {
        return tracker;
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "${jobfinder.scan.cron:0 0 */2 * * *}")
    public void scheduledScan() {
        runScan(true);
    }

    public List<JobPosting> allJobs() {
        int minScoreThreshold = config != null ? config.getMinScore() : 70;
        return repository.findAllByMatchScoreGreaterThanEqualOrderByLastSeenAtDesc(minScoreThreshold);
    }

    public List<JobPosting> eligibleJobs() {
        int minScoreThreshold = config != null ? config.getMinScore() : 70;
        return repository
                .findByEligibleTrueAndMatchScoreGreaterThanEqualOrderByMatchScoreDescLastSeenAtDesc(minScoreThreshold);
    }

    @Transactional
    public Optional<JobPosting> updateCategory(Long id, String category) {
        Optional<JobPosting> opt = repository.findById(id);
        if (opt.isPresent()) {
            JobPosting job = opt.get();
            job.setCategory(category);
            repository.save(job);
            return Optional.of(job);
        }
        return Optional.empty();
    }

    public Map<String, Long> getCategoryCounts() {
        List<JobPosting> jobs = eligibleJobs();
        long pending = jobs.stream().filter(j -> "PENDING".equalsIgnoreCase(j.getCategory())).count();
        long applied = jobs.stream().filter(j -> "APPLIED".equalsIgnoreCase(j.getCategory())).count();
        long notRelevant = jobs.stream().filter(j -> "NOT_RELEVANT".equalsIgnoreCase(j.getCategory())).count();
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("PENDING", pending);
        counts.put("APPLIED", applied);
        counts.put("NOT_RELEVANT", notRelevant);
        counts.put("TOTAL", (long) jobs.size());
        return counts;
    }

    public record ScanStatus(String state, Instant startedAt, int jobsFetched, int qualifyingJobs, List<String> errors,
            String emailStatus) {
    }

    public record ReportJob(JobCandidate candidate, JobScoringService.Result result,
            List<ContactResearchService.ContactLink> contacts) {
    }
}
