package com.manideep.jobfinder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import java.util.*;

@Component
public class GreenhouseAdapter implements JobSourceAdapter {
    private final SourceConfig config;
    private final SourceHealthTracker tracker;
    private final ObjectMapper mapper = new ObjectMapper();

    public GreenhouseAdapter(SourceConfig config, SourceHealthTracker tracker) {
        this.config = config;
        this.tracker = tracker;
    }

    public String sourceName(){return "greenhouse";}
    public List<JobCandidate> fetch() {
        List<JobCandidate> out = Collections.synchronizedList(new ArrayList<>());
        if (config.getGreenhouse() == null) return out;
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (SourceConfig.Board b : config.getGreenhouse()) {
                if (blank(b.getCompany()) || blank(b.getToken())) continue;
                futures.add(executor.submit(() -> {
                    try {
                        String url = "https://boards-api.greenhouse.io/v1/boards/" + b.getToken() + "/jobs?content=true";
                        String body = RestClient.create().get().uri(url).retrieve().body(String.class);
                        if (body == null) {
                            tracker.recordWarning(b.getCompany(), sourceName(), "Empty response received");
                            return;
                        }
                        JsonNode jobs = mapper.readTree(body).path("jobs");
                        int count = 0;
                        for (JsonNode j : jobs) {
                            String link = j.path("absolute_url").asText("");
                            if (link.isBlank()) continue;
                            String desc = j.path("content").asText("");
                            out.add(new JobCandidate(sourceName(), j.path("id").asText(link), b.getCompany(), j.path("title").asText(""),
                                    j.path("location").path("name").asText(""), link, desc, parseDate(j.path("updated_at").asText(null)), extractSalary(desc)));
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
    static Instant parseDate(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim();
        try {
            return Instant.parse(s);
        } catch (Exception ignored) {}
        try {
            return java.time.LocalDate.parse(s).atStartOfDay(java.time.ZoneId.of("UTC")).toInstant();
        } catch (Exception ignored) {}
        try {
            long epoch = Long.parseLong(s);
            if (epoch > 1000000000000L) return Instant.ofEpochMilli(epoch);
            if (epoch > 0) return Instant.ofEpochSecond(epoch);
        } catch (Exception ignored) {}
        String[] patterns = {"MMMM d, yyyy", "MMM d, yyyy", "MMM dd, yyyy", "yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd"};
        for (String p : patterns) {
            try {
                java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern(p, Locale.ENGLISH);
                try {
                    return java.time.LocalDate.parse(s, dtf).atStartOfDay(java.time.ZoneId.of("UTC")).toInstant();
                } catch (Exception e) {
                    return java.time.LocalDateTime.parse(s, dtf).atZone(java.time.ZoneId.of("UTC")).toInstant();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
    static String extractSalary(String s){var m=java.util.regex.Pattern.compile("(?i)(?:₹|INR|Rs\\.?\\s*)?\\s*[\\d,.]+\\s*(?:-|to|–)\\s*(?:₹|INR|Rs\\.?\\s*)?\\s*[\\d,.]+\\s*(?:LPA|lakhs?)").matcher(s==null?"":s);return m.find()?m.group():"";}
    static boolean blank(String s){return s==null||s.isBlank();}
}
