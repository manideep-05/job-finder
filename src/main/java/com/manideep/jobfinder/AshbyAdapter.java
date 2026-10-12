package com.manideep.jobfinder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import java.util.*;

@Component
public class AshbyAdapter implements JobSourceAdapter {
    private final SourceConfig config;
    private final SourceHealthTracker tracker;
    private final ObjectMapper mapper = new ObjectMapper();

    public AshbyAdapter(SourceConfig config, SourceHealthTracker tracker) {
        this.config = config;
        this.tracker = tracker;
    }

    public String sourceName() {
        return "ashby";
    }

    public List<JobCandidate> fetch() {
        List<JobCandidate> out = Collections.synchronizedList(new ArrayList<>());
        if (config.getAshby() == null)
            return out;
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (SourceConfig.Board b : config.getAshby()) {
                if (GreenhouseAdapter.blank(b.getCompany()) || GreenhouseAdapter.blank(b.getName()))
                    continue;
                futures.add(executor.submit(() -> {
                    try {
                        String url = "https://api.ashbyhq.com/posting-api/job-board/" + b.getName()
                                + "?includeCompensation=true";
                        String body = RestClient.create().get().uri(url).retrieve().body(String.class);
                        if (body == null) {
                            tracker.recordWarning(b.getCompany(), sourceName(), "Empty response received");
                            return;
                        }
                        JsonNode jobs = mapper.readTree(body).path("jobs");
                        int count = 0;
                        for (JsonNode j : jobs) {
                            String link = j.path("jobUrl").asText(j.path("applyUrl").asText(""));
                            if (link.isBlank())
                                continue;
                            String desc = j.path("descriptionPlain").asText(j.path("descriptionHtml").asText(""));
                            String comp = cleanCompensation(j.path("compensation"));
                            out.add(new JobCandidate(sourceName(), j.path("id").asText(link), b.getCompany(),
                                    j.path("title").asText(""),
                                    j.path("location").asText(j.path("locationName").asText("")), link, desc,
                                    GreenhouseAdapter.parseDate(j.path("publishedAt").asText(null)), comp));
                            count++;
                        }
                        tracker.recordSuccess(b.getCompany(), sourceName(), count);
                    } catch (Exception ex) {
                        tracker.recordFailure(b.getCompany(), sourceName(),
                                ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    }
                }));
            }
            for (var f : futures) {
                try {
                    f.get();
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }

    private String cleanCompensation(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull())
            return "";
        if (node.hasNonNull("compensationSummary"))
            return node.path("compensationSummary").asText("");
        if (node.hasNonNull("compensationTierSummary"))
            return node.path("compensationTierSummary").asText("");
        String s = node.toString();
        return s.length() > 400 ? s.substring(0, 400) : s;
    }
}
