package com.manideep.jobfinder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import java.util.*;

@Component
public class LeverAdapter implements JobSourceAdapter {
    private final SourceConfig config;
    private final SourceHealthTracker tracker;
    private final ObjectMapper mapper = new ObjectMapper();

    public LeverAdapter(SourceConfig config, SourceHealthTracker tracker) {
        this.config = config;
        this.tracker = tracker;
    }

    public String sourceName(){return "lever";}
    public List<JobCandidate> fetch() {
        List<JobCandidate> out = Collections.synchronizedList(new ArrayList<>());
        if (config.getLever() == null) return out;
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (SourceConfig.Board b : config.getLever()) {
                if (GreenhouseAdapter.blank(b.getCompany()) || GreenhouseAdapter.blank(b.getSite())) continue;
                futures.add(executor.submit(() -> {
                    try {
                        String url = "https://api.lever.co/v0/postings/" + b.getSite() + "?mode=json";
                        String body = RestClient.create().get().uri(url).retrieve().body(String.class);
                        if (body == null) {
                            tracker.recordWarning(b.getCompany(), sourceName(), "Empty response received");
                            return;
                        }
                        JsonNode jobs = mapper.readTree(body);
                        int count = 0;
                        for (JsonNode j : jobs) {
                            String link = j.path("hostedUrl").asText(j.path("applyUrl").asText(""));
                            if (link.isBlank()) continue;
                            String desc = j.path("descriptionPlain").asText("") + " " + j.path("additionalPlain").asText("");
                            JsonNode lists = j.path("lists");
                            for (JsonNode list : lists) desc += " " + list.path("content").asText("");
                            long ms = j.path("createdAt").asLong(0);
                            Instant date = ms > 0 ? Instant.ofEpochMilli(ms) : null;
                            String salary = j.path("salaryRange").isMissingNode() ? "" : j.path("salaryRange").toString();
                            if (salary.length() > 400) salary = salary.substring(0, 400);
                            out.add(new JobCandidate(sourceName(), j.path("id").asText(link), b.getCompany(), j.path("text").asText(""),
                                    j.path("categories").path("location").asText(""), link, desc, date, salary));
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
}
