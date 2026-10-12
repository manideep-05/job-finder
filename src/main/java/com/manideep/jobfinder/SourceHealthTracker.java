package com.manideep.jobfinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SourceHealthTracker {
    private static final Logger log = LoggerFactory.getLogger(SourceHealthTracker.class);

    public record HealthRecord(
            String company,
            String source,
            String status, // HEALTHY, WARNING, FAILED
            int jobsFound,
            String message,
            Instant lastChecked
    ) {}

    private final SourceErrorLogRepository errorLogRepository;
    private final Map<String, HealthRecord> records = new ConcurrentHashMap<>();

    public SourceHealthTracker(SourceErrorLogRepository errorLogRepository) {
        this.errorLogRepository = errorLogRepository;
    }

    public void recordSuccess(String company, String source, int jobsFound) {
        records.put(company, new HealthRecord(company, source, "HEALTHY", jobsFound, jobsFound + " jobs fetched successfully", Instant.now()));
        log.info("[Source Health] {} ({}): HEALTHY ({} jobs)", company, source, jobsFound);
    }

    public void recordWarning(String company, String source, String warningMessage) {
        records.put(company, new HealthRecord(company, source, "WARNING", 0, warningMessage, Instant.now()));
        log.warn("[Source Health] {} ({}): WARNING - {}", company, source, warningMessage);
        try {
            errorLogRepository.save(new SourceErrorLog(company, source, "WARNING", warningMessage));
        } catch (Exception ex) {
            log.error("Could not persist warning log to DB: {}", ex.getMessage());
        }
    }

    public void recordFailure(String company, String source, String errorMessage) {
        records.put(company, new HealthRecord(company, source, "FAILED", 0, errorMessage, Instant.now()));
        log.error("[Source Health] {} ({}): FAILED - {}", company, source, errorMessage);
        try {
            errorLogRepository.save(new SourceErrorLog(company, source, "FAILED", errorMessage));
        } catch (Exception ex) {
            log.error("Could not persist error log to DB: {}", ex.getMessage());
        }
    }

    /**
     * Automatically deletes error logs older than 4 days.
     * Runs daily at 2:00 AM.
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "${jobfinder.error-retention.cron:0 0 2 * * *}")
    public void purgeExpiredLogs() {
        try {
            Instant cutoff = Instant.now().minus(4, java.time.temporal.ChronoUnit.DAYS);
            int deleted = errorLogRepository.deleteLogsOlderThan(cutoff);
            log.info("[Source Health Retention] Purged {} error log(s) older than 4 days (cutoff: {})", deleted, cutoff);
        } catch (Exception ex) {
            log.error("[Source Health Retention] Failed to purge expired logs: {}", ex.getMessage());
        }
    }

    public List<SourceErrorLog> getRecentErrorLogs() {
        try {
            return errorLogRepository.findTop100ByOrderByCreatedAtDesc();
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    public List<HealthRecord> getAllRecords() {
        return records.values().stream()
                .sorted(Comparator.comparing(HealthRecord::status).reversed().thenComparing(HealthRecord::company))
                .toList();
    }

    public List<HealthRecord> getIssues() {
        return records.values().stream()
                .filter(r -> !"HEALTHY".equals(r.status()))
                .sorted(Comparator.comparing(HealthRecord::status).reversed().thenComparing(HealthRecord::company))
                .toList();
    }
}
