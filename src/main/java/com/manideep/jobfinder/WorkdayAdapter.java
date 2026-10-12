package com.manideep.jobfinder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.net.URI;
import java.time.Instant;
import java.util.*;

/** Reads public Workday career boards. Configure careersUrl as the full public board URL,
 * e.g. https://tenant.wd5.myworkdayjobs.com/en-US/SiteName. */
@Component
public class WorkdayAdapter implements JobSourceAdapter {
    private final SourceConfig config;
    private final SourceHealthTracker tracker;
    private final ObjectMapper mapper = new ObjectMapper();

    public WorkdayAdapter(SourceConfig config, SourceHealthTracker tracker) {
        this.config = config;
        this.tracker = tracker;
    }

    public String sourceName() { return "workday"; }
    public List<JobCandidate> fetch() throws Exception {
        List<JobCandidate> out = Collections.synchronizedList(new ArrayList<>());
        if (config.getWorkday() == null) return out;

        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (SourceConfig.Board b : config.getWorkday()) {
                if (GreenhouseAdapter.blank(b.getCompany()) || GreenhouseAdapter.blank(b.getCareersUrl())) continue;
                futures.add(executor.submit(() -> {
                    int companyFound = 0;
                    try {
                        URI board = URI.create(b.getCareersUrl());
                        String host = board.getScheme() + "://" + board.getAuthority();
                        String[] parts = board.getPath().split("/");
                        String site = "";
                        for (int i = 0; i < parts.length; i++) {
                            if (parts[i].equalsIgnoreCase("en-US") || parts[i].matches("[a-z]{2}-[A-Z]{2}")) {
                                if (i + 1 < parts.length) site = parts[i + 1];
                                break;
                            }
                        }
                        if (site.isBlank()) { for (int i=parts.length-1;i>=0;i--) if(!parts[i].isBlank()) {site=parts[i];break;} }
                        String tenant = board.getHost().split("\\.")[0];
                        if (site.isBlank() || tenant.isBlank()) {
                            tracker.recordWarning(b.getCompany(), sourceName(), "Could not resolve tenant or site path from URL");
                            return;
                        }
                        String api = host + "/wday/cxs/" + tenant + "/" + site + "/jobs";
                        int offset = 0;
                        while (offset < 40) {
                            String body = mapper.createObjectNode()
                                    .put("limit", 20)
                                    .put("offset", offset)
                                    .put("searchText", "Software Engineer")
                                    .set("appliedFacets", mapper.createObjectNode()).toString();
                            String response = RestClient.create().post()
                                    .uri(api)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                    .header("Accept", "application/json")
                                    .body(body)
                                    .retrieve()
                                    .body(String.class);
                            if (response == null) break;
                            JsonNode root = mapper.readTree(response);
                            JsonNode jobs = root.path("jobPostings");
                            if (!jobs.isArray() || jobs.isEmpty()) break;
                            for (JsonNode j : jobs) {
                                String path = j.path("externalPath").asText("");
                                String link = path.startsWith("http") ? path : host + path;
                                if (path.isBlank()) continue;
                                String title = j.path("title").asText("");
                                String location = j.path("locationsText").asText("India");
                                String desc = String.join(" ", iterableStrings(j.path("bulletFields")));
                                if (desc.length() < 80) {
                                    String deepDesc = fetchJobDescription(host, tenant, site, path);
                                    if (!deepDesc.isBlank()) desc = deepDesc;
                                }
                                if (desc.isBlank()) desc = title;
                                String cleanPath = path.startsWith("/") ? path.substring(1) : path;
                                String externalId = b.getCompany() + "_" + (cleanPath.length() > 240 ? Math.abs(cleanPath.hashCode()) : cleanPath);
                                out.add(new JobCandidate(sourceName(), externalId, b.getCompany(), title, location, link, desc, Instant.now(), ""));
                                companyFound++;
                            }
                            offset += jobs.size();
                            int total = root.path("total").asInt(offset);
                            if (offset >= total) break;
                        }
                        tracker.recordSuccess(b.getCompany(), sourceName(), companyFound);
                    } catch (Exception ex) {
                        String msg = ex.getMessage() == null ? "" : ex.getMessage();
                        if (msg.contains("422")) {
                            tracker.recordWarning(b.getCompany(), sourceName(), "Workday site slug mismatch or tenant configuration requires exact case (422 Unprocessable Entity)");
                        } else if (msg.contains("403")) {
                            tracker.recordWarning(b.getCompany(), sourceName(), "Workday Akamai bot protection active (403 Forbidden)");
                        } else {
                            tracker.recordFailure(b.getCompany(), sourceName(), ex.getClass().getSimpleName() + ": " + ex.getMessage());
                        }
                    }
                }));
            }
            for (var f : futures) {
                try { f.get(); } catch (Exception ignored) {}
            }
        }
        return out;
    }

    private String fetchJobDescription(String host, String tenant, String site, String path) {
        try {
            String jobSuffix = path.contains("/job/") ? path.substring(path.indexOf("/job/")) : (path.startsWith("/") ? path : "/" + path);
            String detailUri = host + "/wday/cxs/" + tenant + "/" + site + jobSuffix;
            String res = RestClient.create().get()
                    .uri(detailUri)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(String.class);
            if (res != null) {
                JsonNode root = mapper.readTree(res);
                String fullDesc = root.path("jobPostingInfo").path("jobDescription").asText("");
                if (!fullDesc.isBlank()) {
                    return org.jsoup.Jsoup.parse(fullDesc).text();
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private List<String> iterableStrings(JsonNode node) { List<String> values = new ArrayList<>(); if(node.isArray()) for(JsonNode n:node) values.add(n.asText("")); return values; }
}
