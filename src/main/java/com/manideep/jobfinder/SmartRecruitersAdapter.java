package com.manideep.jobfinder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;

@Component
public class SmartRecruitersAdapter implements JobSourceAdapter {
    private final SourceConfig config;
    private final SourceHealthTracker tracker;
    private final ObjectMapper mapper = new ObjectMapper();

    public SmartRecruitersAdapter(SourceConfig config, SourceHealthTracker tracker) {
        this.config = config;
        this.tracker = tracker;
    }

    @Override
    public String sourceName() {
        return "smartrecruiters";
    }

    @Override
    public List<JobCandidate> fetch() {
        List<JobCandidate> out = Collections.synchronizedList(new ArrayList<>());
        if (config.getSmartrecruiters() == null || config.getSmartrecruiters().isEmpty()) {
            return out;
        }

        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (SourceConfig.Board b : config.getSmartrecruiters()) {
                String id = b.getIdentifier();
                if (GreenhouseAdapter.blank(b.getCompany()) || GreenhouseAdapter.blank(id)) {
                    continue;
                }

                futures.add(executor.submit(() -> {
                    try {
                        String url = "https://api.smartrecruiters.com/v1/companies/" + id + "/postings?limit=100&country=in";
                        String body = RestClient.create().get()
                                .uri(url)
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                .header("Accept", "application/json")
                                .retrieve()
                                .body(String.class);

                        if (body == null || body.isBlank()) {
                            tracker.recordWarning(b.getCompany(), sourceName(), "Empty response received");
                            return;
                        }

                        JsonNode root = mapper.readTree(body);
                        JsonNode content = root.path("content");
                        if (!content.isArray()) {
                            tracker.recordWarning(b.getCompany(), sourceName(), "Invalid JSON payload structure");
                            return;
                        }

                        int count = 0;
                        for (JsonNode p : content) {
                            String jobId = p.path("id").asText("");
                            String title = p.path("name").asText("");
                            if (jobId.isBlank() || title.isBlank()) continue;

                            String city = p.path("location").path("city").asText("India");
                            String location = city.equalsIgnoreCase("India") ? "India" : city + ", India";
                            String link = "https://jobs.smartrecruiters.com/" + id + "/" + jobId;
                            String pubStr = p.path("releasedDate").asText(null);
                            Instant publishedAt = pubStr != null ? GreenhouseAdapter.parseDate(pubStr) : Instant.now();

                            String desc = fetchDetail(id, jobId, title);

                            out.add(new JobCandidate(sourceName(), b.getCompany() + "_" + jobId, b.getCompany(), title, location, link, desc, publishedAt, ""));
                            count++;
                        }

                        tracker.recordSuccess(b.getCompany(), sourceName(), count);
                    } catch (Exception ex) {
                        tracker.recordFailure(b.getCompany(), sourceName(), ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    }
                }));
            }
            for (var f : futures) {
                try { f.get(); } catch (Exception ignored) {}
            }
        }
        return out;
    }

    private String fetchDetail(String companyId, String jobId, String defaultTitle) {
        try {
            String detailUrl = "https://api.smartrecruiters.com/v1/companies/" + companyId + "/postings/" + jobId;
            String res = RestClient.create().get()
                    .uri(detailUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .retrieve()
                    .body(String.class);
            if (res != null) {
                JsonNode root = mapper.readTree(res);
                JsonNode sections = root.path("jobAd").path("sections");
                String jd = sections.path("jobDescription").path("text").asText("");
                String qual = sections.path("qualifications").path("text").asText("");
                String full = org.jsoup.Jsoup.parse(jd + " " + qual).text().trim();
                if (!full.isBlank()) return full;
            }
        } catch (Exception ignored) {}
        return defaultTitle;
    }
}
