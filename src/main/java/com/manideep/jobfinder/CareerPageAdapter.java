package com.manideep.jobfinder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;

@Component
public class CareerPageAdapter implements JobSourceAdapter {
    private final SourceConfig config;
    private final SourceHealthTracker tracker;
    private final ObjectMapper mapper = new ObjectMapper();

    public CareerPageAdapter(SourceConfig config, SourceHealthTracker tracker) {
        this.config = config;
        this.tracker = tracker;
    }

    @Override
    public String sourceName() {
        return "career-pages";
    }

    @Override
    public List<JobCandidate> fetch() {
        List<JobCandidate> out = Collections.synchronizedList(new ArrayList<>());
        if (config.getCareerPages() == null)
            return out;

        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (SourceConfig.CareerPage cp : config.getCareerPages()) {
                if (cp.getCompany() == null || cp.getUrl() == null || cp.getUrl().isBlank())
                    continue;
                futures.add(executor.submit(() -> {
                    String comp = cp.getCompany();
                    try {
                        List<JobCandidate> list;
                        if (comp.equalsIgnoreCase("Amazon")) {
                            list = fetchAmazonJobs();
                        } else if (comp.equalsIgnoreCase("Microsoft")) {
                            list = fetchMicrosoftJobs();
                        } else if (comp.equalsIgnoreCase("Google")) {
                            list = fetchGoogleJobs();
                        } else if (comp.equalsIgnoreCase("Apple")) {
                            list = fetchAppleJobs();
                        } else {
                            list = scrapeCareerPage(cp);
                        }

                        if (list != null && !list.isEmpty()) {
                            out.addAll(list);
                            tracker.recordSuccess(comp, sourceName(), list.size());
                        } else {
                            if (comp.equalsIgnoreCase("Amazon") || comp.equalsIgnoreCase("Microsoft") 
                                    || comp.equalsIgnoreCase("Google") || comp.equalsIgnoreCase("Apple")) {
                                tracker.recordWarning(comp, sourceName(), "0 openings found for Software Engineer in India");
                            } else {
                                tracker.recordWarning(comp, sourceName(), "Client-side SPA (0 links in static HTML; requires JSON search endpoint)");
                            }
                        }
                    } catch (Exception ex) {
                        String msg = ex.getMessage() == null ? "" : ex.getMessage();
                        if (msg.contains("401 Unauthorized") || msg.contains("Unauthorized")) {
                            tracker.recordWarning(comp, sourceName(), "Protected career portal (401 Unauthorized; requires interactive browser session / CSRF token)");
                        } else if (msg.contains("No subject alternative DNS name") || msg.contains("SSL")) {
                            tracker.recordWarning(comp, sourceName(), "Internal search endpoint SSL hostname mismatch (migrated to new platform)");
                        } else if (msg.contains("404 Not Found")) {
                            tracker.recordWarning(comp, sourceName(), "Search API endpoint deprecated or moved (404 Not Found)");
                        } else if (msg.contains("SocketTimeoutException") || msg.contains("timed out") || msg.contains("Timeout")) {
                            tracker.recordWarning(comp, sourceName(), "Portal connection timed out; slow or blocking automated requests");
                        } else {
                            tracker.recordFailure(comp, sourceName(), ex.getClass().getSimpleName() + ": " + ex.getMessage());
                        }
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

    private List<JobCandidate> fetchAmazonJobs() throws Exception {
        List<JobCandidate> list = new ArrayList<>();
        String url = "https://www.amazon.jobs/en/search.json?base_query=Software+Development+Engineer&country=IND&result_limit=25";
        String json = RestClient.create().get()
                .uri(url)
                .header("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .retrieve()
                .body(String.class);
        if (json == null)
            return list;
        JsonNode root = mapper.readTree(json);
        JsonNode jobs = root.path("jobs");
        for (JsonNode j : jobs) {
            String id = j.path("id_icims").asText(j.path("id").asText(""));
            String title = j.path("title").asText("");
            String jobPath = j.path("job_path").asText("");
            String jobUrl = jobPath.startsWith("http") ? jobPath : "https://www.amazon.jobs" + jobPath;
            String location = j.path("location").asText(j.path("city").asText("") + ", India");
            String desc = j.path("description").asText("");
            String postedDate = j.path("posted_date").asText(null);
            Instant pub = postedDate != null ? GreenhouseAdapter.parseDate(postedDate) : null;
            list.add(new JobCandidate(sourceName(), id, "Amazon", title, location, jobUrl, desc, pub, ""));
        }
        return list;
    }

    private List<JobCandidate> fetchMicrosoftJobs() throws Exception {
        List<JobCandidate> list = new ArrayList<>();
        String url = "https://gcsservices.careers.microsoft.com/search/api/v1/search?q=software%20engineer&lc=India&p=1&rt=none";
        String json = RestClient.create().get()
                .uri(url)
                .header("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .retrieve()
                .body(String.class);
        if (json == null)
            return list;
        JsonNode root = mapper.readTree(json);
        JsonNode jobs = root.path("operationResult").path("result").path("jobs");
        for (JsonNode j : jobs) {
            String id = j.path("jobId").asText("");
            String title = j.path("title").asText("");
            if (title.isBlank())
                continue;
            String jobUrl = "https://jobs.careers.microsoft.com/global/en/share/" + id;
            String location = j.path("properties").path("primaryLocation").asText("India");
            String desc = j.path("properties").path("description").asText(title);
            String postingDate = j.path("postingDate").asText(null);
            Instant pub = postingDate != null ? GreenhouseAdapter.parseDate(postingDate) : null;
            list.add(new JobCandidate(sourceName(), id, "Microsoft", title, location, jobUrl, desc, pub, ""));
        }
        return list;
    }

    private List<JobCandidate> fetchGoogleJobs() throws Exception {
        List<JobCandidate> list = new ArrayList<>();
        String url = "https://careers.google.com/api/v3/search/?q=software%20engineer&location=India";
        String json = RestClient.create().get()
                .uri(url)
                .header("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .retrieve()
                .body(String.class);
        if (json == null)
            return list;
        JsonNode root = mapper.readTree(json);
        JsonNode jobs = root.path("jobs");
        for (JsonNode j : jobs) {
            String id = j.path("id").asText("");
            String title = j.path("title").asText("");
            if (title.isBlank())
                continue;
            String jobUrl = "https://www.google.com/about/careers/applications/jobs/results/" + id;
            JsonNode locs = j.path("locations");
            String location = locs.isArray() && !locs.isEmpty() ? locs.get(0).asText("India") : "India";
            String desc = j.path("description").asText(title);
            String pubDate = j.path("publish_day").asText(null);
            Instant pub = pubDate != null ? GreenhouseAdapter.parseDate(pubDate) : null;
            list.add(new JobCandidate(sourceName(), id, "Google", title, location, jobUrl, desc, pub, ""));
        }
        return list;
    }

    private List<JobCandidate> fetchAppleJobs() throws Exception {
        List<JobCandidate> list = new ArrayList<>();
        Exception postEx = null;
        // 1. Try Apple POST role/search API with session handshake
        try {
            String cookieHeader = "";
            try {
                org.jsoup.Connection.Response resp = Jsoup.connect("https://jobs.apple.com/en-in/search")
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .timeout(4000)
                        .execute();
                Map<String, String> cookies = resp.cookies();
                if (!cookies.isEmpty()) {
                    StringBuilder cb = new StringBuilder();
                    for (var e : cookies.entrySet()) {
                        if (!cb.isEmpty()) cb.append("; ");
                        cb.append(e.getKey()).append("=").append(e.getValue());
                    }
                    cookieHeader = cb.toString();
                }
            } catch (Exception ignored) {}

            String payload = "{\"query\":\"software engineer\",\"filters\":{\"postingpostLocation\":[\"country--IND\"]},\"page\":1,\"locale\":\"en-in\",\"sort\":\"relevance\"}";
            var request = RestClient.create().post()
                    .uri("https://jobs.apple.com/api/role/search")
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json");
            if (!cookieHeader.isBlank()) {
                request.header("Cookie", cookieHeader);
            }
            String json = request.body(payload)
                    .retrieve()
                    .body(String.class);

            if (json != null && !json.isBlank()) {
                JsonNode root = mapper.readTree(json);
                JsonNode results = root.path("searchResults");
                if (results.isArray()) {
                    for (JsonNode r : results) {
                        String id = r.path("id").asText("");
                        String title = r.path("postingTitle").asText("");
                        if (title.isBlank())
                            title = r.path("title").asText("");
                        if (title.isBlank())
                            continue;

                        String slug = r.path("transformedPostingTitle").asText("");
                        String jobUrl = "https://jobs.apple.com/en-in/details/" + id
                                + (slug.isBlank() ? "" : "/" + slug);

                        StringBuilder locBuilder = new StringBuilder();
                        JsonNode locs = r.path("locations");
                        if (locs.isArray() && !locs.isEmpty()) {
                            for (JsonNode l : locs) {
                                String city = l.path("name").asText(l.path("city").asText(""));
                                if (!city.isBlank()) {
                                    if (!locBuilder.isEmpty())
                                        locBuilder.append(", ");
                                    locBuilder.append(city);
                                }
                            }
                        }
                        String location = locBuilder.isEmpty() ? "India" : locBuilder.toString() + ", India";
                        String desc = r.path("jobSummary").asText(title);
                        String pubDate = r.path("postDateInGMT").asText(null);
                        Instant pub = pubDate != null ? GreenhouseAdapter.parseDate(pubDate) : null;
                        list.add(new JobCandidate(sourceName(), id, "Apple", title, location, jobUrl, desc, pub, ""));
                    }
                }
            }
        } catch (Exception ex) {
            postEx = ex;
        }

        // 2. Fallback to Apple GET search API if list is empty
        if (list.isEmpty()) {
            try {
                String json = RestClient.create().get()
                        .uri("https://jobs.apple.com/api/v1/search?query=software%20engineer&location=India")
                        .header("User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Accept", "application/json")
                        .retrieve()
                        .body(String.class);
                if (json != null && !json.isBlank()) {
                    JsonNode root = mapper.readTree(json);
                    JsonNode results = root.path("searchResults");
                    if (results.isArray()) {
                        for (JsonNode r : results) {
                            String id = r.path("id").asText("");
                            String title = r.path("postingTitle").asText(r.path("title").asText(""));
                            if (title.isBlank())
                                continue;
                            String jobUrl = "https://jobs.apple.com/en-in/details/" + id;
                            list.add(new JobCandidate(sourceName(), id, "Apple", title, "India", jobUrl, title,
                                    Instant.now(), ""));
                        }
                    }
                }
            } catch (Exception getEx) {
                if (postEx != null) throw postEx;
                throw getEx;
            }
        }
        if (list.isEmpty() && postEx != null) {
            throw postEx;
        }
        return list;
    }

    private List<JobCandidate> scrapeCareerPage(SourceConfig.CareerPage cp) throws Exception {
        List<JobCandidate> list = new ArrayList<>();
        Document doc = Jsoup.connect(cp.getUrl())
                .userAgent(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(12000)
                .get();

        Elements links = doc.select("a[href]");
        for (Element a : links) {
            String title = a.text().trim();
            String href = a.absUrl("href");
            if (title.length() < 5 || href.isBlank())
                continue;
            String lower = title.toLowerCase();
            if ((lower.contains("software") || lower.contains("engineer") || lower.contains("developer")
                    || lower.contains("backend") || lower.contains("sde"))
                    && !lower.contains("cookie") && !lower.contains("privacy") && !lower.contains("terms")) {
                String id = cp.getCompany() + "_" + Math.abs(href.hashCode());
                list.add(new JobCandidate(sourceName(), id, cp.getCompany(), title, "India", href, title,
                        Instant.now(), ""));
            }
        }
        return list;
    }
}
