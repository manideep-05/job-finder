package com.manideep.jobfinder;

import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JobScoringService {

    // ==========================================
    // 1. ROLE MATCH DEFINITIONS (Max 25 Pts)
    // ==========================================
    private static final List<String> EXACT_SDE_TITLES = List.of(
        "sde 2", "sde ii", "sde-2", "sde-ii", "sde 1", "sde i", "sde-1", "sde-i",
        "software development engineer ii", "software development engineer i", "software development engineer 2",
        "software engineer ii", "software engineer 2", "software engineer i", "software engineer 1",
        "backend engineer ii", "backend engineer 2", "member of technical staff", "mts 2", "mts 1", "mts"
    );

    private static final List<String> GENERAL_TECH_TITLES = List.of(
        "software engineer", "software development engineer", "backend engineer", "backend developer",
        "platform engineer", "systems engineer", "cloud software engineer", "full stack engineer"
    );

    private static final List<String> SENIOR_EXCLUDES = List.of(
        "principal", "staff engineer", "director", "engineering manager", "architect",
        "tech lead", "team lead", "lead engineer", "lead software", "lead developer", "head of"
    );

    // ==========================================
    // 2. GENERIC INDUSTRY SKILL CLUSTERS (Max 50 Pts - 10 Pts per Cluster)
    // Calibrated directly from top tech SDE 1 & SDE 2 JDs
    // ==========================================
    private static final Map<String, List<String>> SKILL_CLUSTERS = Map.of(
        "Languages & Core OOP", List.of(
            "java", "python", "multithreading", "concurrency", "core java", "jvm",
            "collections", "data structures", "algorithms", "dsa", "oop", "object-oriented",
            "object oriented", "design patterns", "clean code", "separation of concerns", "abstractions"
        ),
        "Frameworks & API Architecture", List.of(
            "spring boot", "spring", "microservices", "micro-services", "rest api", "restful",
            "api design", "api contract", "domain-driven", "ddd", "hibernate", "jpa",
            "grpc", "rpc", "graphql", "low-level design", "lld"
        ),
        "Databases & Caching", List.of(
            "postgresql", "postgres", "oracle", "mysql", "sql", "rdbms", "transact sql",
            "redis", "caching", "nosql", "dynamodb", "cassandra", "mongodb", "elastic",
            "elasticsearch", "data modeling", "indexing", "acid"
        ),
        "Distributed Systems & Messaging", List.of(
            "distributed systems", "distributed programming", "system design", "scalability", "scaling",
            "kafka", "rabbitmq", "message queue", "distributed messaging", "asynchronous",
            "event-driven", "pub/sub", "pub-sub", "sqs", "high availability", "reliability", "latency"
        ),
        "Cloud, DevOps & Engineering Excellence", List.of(
            "aws", "gcp", "azure", "docker", "kubernetes", "k8s", "ci/cd", "ci or cd", "continuous integration",
            "git", "jenkins", "github actions", "unit testing", "integration testing", "tdd", "junit", "mockito",
            "observability", "datadog", "splunk", "monitoring", "alerting", "copilot", "ai-assisted"
        )
    );

    // ==========================================
    // 3. LOCATION HUBS (Max 10 Pts)
    // ==========================================
    private static final List<String> INDIA_HUBS = List.of(
        "india", "bengaluru", "bangalore", "hyderabad", "pune", "chennai",
        "gurugram", "gurgaon", "noida", "delhi", "mumbai", "kolkata",
        "ahmedabad", "kochi", "coimbatore", "trivandrum", "chandigarh", "indore", "jaipur"
    );

    private static final List<String> REGIONAL_EXCLUDES = List.of(
        "us only", "usa only", "north america", "emea", "latam", "europe", "uk only", "canada only",
        "united states", "usa", "san francisco", "seattle", "new york", "austin", "california", "washington",
        "london", "united kingdom", "germany", "berlin", "canada", "toronto", "vancouver",
        "australia", "sydney", "singapore", "tokyo", "japan", "ireland", "dublin", "netherlands", "amsterdam",
        "poland", "warsaw", "france", "paris", "israel", "tel aviv"
    );

    // ==========================================
    // 4. EXPERIENCE REGEX PATTERNS (Supports decimals e.g. 2.6+ years)
    // ==========================================
    private static final Pattern EXP_PLUS_PATTERN = Pattern.compile("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(?:\\+|plus)\\s*(?:years?|yrs?)");
    private static final Pattern EXP_MIN_PATTERN = Pattern.compile("(?i)\\b(?:minimum|min|at least)\\s*(?:of\\s*)?(\\d+(?:\\.\\d+)?)\\s*(?:years?|yrs?)");
    private static final Pattern EXP_RANGE_PATTERN = Pattern.compile("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(?:-|to|–)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:years?|yrs?)");
    private static final Pattern EXP_YEARS_PATTERN = Pattern.compile("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(?:years?|yrs?)\\s+of");

    private final SourceConfig config;

    public JobScoringService(SourceConfig config) {
        this.config = config;
    }

    public Result score(JobCandidate j, boolean newThisRun) {
        String title = lower(j.title());
        String location = lower(j.location());
        String text = title + " " + lower(j.description());

        List<String> reasons = new ArrayList<>();
        List<String> matchedSkills = new ArrayList<>();

        // ----------------------------------------------------
        // PILLAR 1: ROLE MATCH (15 Pts Max) - Title is gating
        // ----------------------------------------------------
        int roleScore = 0;
        boolean excludedRole = SENIOR_EXCLUDES.stream().anyMatch(title::contains) || title.contains("intern");
        if (excludedRole) {
            reasons.add("Role: Excluded senior leadership or intern role.");
            return new Result(0, false, null, matchedSkills, reasons, "excluded_role", newThisRun);
        } else if (EXACT_SDE_TITLES.stream().anyMatch(title::contains)) {
            roleScore = 15;
            reasons.add("Role (15/15): Exact SDE 1 / SDE 2 / MTS match.");
        } else if (GENERAL_TECH_TITLES.stream().anyMatch(title::contains)) {
            roleScore = 12;
            reasons.add("Role (12/15): Software Engineer / Backend match.");
        } else if (title.contains("engineer") || title.contains("developer")) {
            roleScore = 8;
            reasons.add("Role (8/15): General engineering role.");
        } else {
            roleScore = 4;
            reasons.add("Role (4/15): Title match weak.");
        }

        // ----------------------------------------------------
        // PILLAR 2: GENERIC SKILL CLUSTERS (50 Pts Max - Heavyweight)
        // Calibrated for:
        // - Concise Tier-1 JDs (Google, Meta, Apple with 3-5 high-bar bullets)
        // - Comprehensive tech stack JDs
        // - Resilient baseline when ATS API provides title-only
        // ----------------------------------------------------
        int skillScore = 0;
        List<String> matchedClusters = new ArrayList<>();
        for (var entry : SKILL_CLUSTERS.entrySet()) {
            String clusterName = entry.getKey();
            List<String> keywords = entry.getValue();
            List<String> hits = keywords.stream().filter(text::contains).toList();
            if (!hits.isEmpty()) {
                matchedClusters.add(clusterName);
                matchedSkills.addAll(hits);
            }
        }

        String rawDesc = j.description() == null ? "" : j.description().trim();
        boolean isMissingDescription = rawDesc.isBlank() 
                || rawDesc.equalsIgnoreCase(j.title().trim()) 
                || rawDesc.length() < 70;
        boolean isConciseJd = !isMissingDescription && rawDesc.length() < 600;

        if (isMissingDescription) {
            // Case 1: Job Description could not be fetched from ATS API
            if (title.contains("backend") || title.contains("java") || title.contains("systems") || title.contains("platform")) {
                skillScore = 38;
                reasons.add("Skills (38/50): ATS provided title only; backend-specialized engineering credit awarded.");
            } else {
                skillScore = 30;
                reasons.add("Skills (30/50): ATS provided title only; baseline engineering credit awarded (pending full JD).");
            }
        } else if (isConciseJd) {
            // Case 2: Concise Tier-1 JD (e.g. Google, Apple, Meta with 3-5 core fundamental requirements)
            int clusters = matchedClusters.size();
            if (clusters >= 2) {
                skillScore = 50; // 100% match on all core requirements asked in the JD
                reasons.add("Skills (50/50): 100% match across all core technical pillars in concise Tier-1 JD [" + String.join(", ", matchedClusters) + "]");
            } else if (clusters == 1) {
                skillScore = 35;
                reasons.add("Skills (35/50): Matched core primary cluster in concise JD [" + String.join(", ", matchedClusters) + "]");
            } else {
                skillScore = 15;
                reasons.add("Skills (15/50): Concise JD with minimal tech keyword overlap.");
            }
        } else {
            // Case 3: Comprehensive JD (lists full tech stack requirements)
            int clusters = matchedClusters.size();
            if (clusters >= 4) {
                skillScore = 50;
            } else if (clusters == 3) {
                skillScore = 42;
            } else if (clusters == 2) {
                skillScore = 32;
            } else if (clusters == 1) {
                skillScore = 20;
            } else {
                skillScore = 0;
            }
            if (!matchedClusters.isEmpty()) {
                reasons.add("Skills (" + skillScore + "/50): Matched " + clusters + " tech clusters [" + String.join(", ", matchedClusters) + "]");
            } else {
                reasons.add("Skills (0/50): No core backend skill keywords identified in full JD.");
            }
        }

        // ----------------------------------------------------
        // PILLAR 3: EXPERIENCE MATCH (15 Pts Max) - Hard Gating (<= 5 Yrs Only)
        // ----------------------------------------------------
        int expScore = 12;
        double detectedMinYears = extractMinExperience(text);

        if (detectedMinYears > 5.0) {
            reasons.add("Experience: Excluded (requires " + detectedMinYears + "+ years of experience; hard limit is 5 years).");
            return new Result(0, false, null, matchedSkills, reasons, "excluded_experience", newThisRun);
        } else if (detectedMinYears >= 5.0 && (title.contains("senior") || title.contains("sr."))) {
            reasons.add("Experience: Excluded (Senior role requiring " + detectedMinYears + "+ years).");
            return new Result(0, false, null, matchedSkills, reasons, "excluded_experience", newThisRun);
        } else if (detectedMinYears >= 1.5 && detectedMinYears <= 5.0) {
            expScore = 15; // Prime target sweet spot (1.5 - 5 yrs e.g. 2+, 2.6+, 3+, 4 yrs)
            reasons.add("Experience (15/15): Target experience match (" + detectedMinYears + "+ yrs required).");
        } else if (detectedMinYears >= 0.5 && detectedMinYears < 1.5) {
            expScore = 13;
            reasons.add("Experience (13/15): SDE-1 match (" + detectedMinYears + "+ yrs required).");
        } else if (detectedMinYears == 0.0) {
            expScore = 11;
            reasons.add("Experience (11/15): Entry/Early career match.");
        } else {
            expScore = 12;
            reasons.add("Experience (12/15): Experience requirement flexible/general.");
        }

        // ----------------------------------------------------
        // PILLAR 4: LOCATION MATCH (Hard Gating - India Only)
        // ----------------------------------------------------
        boolean locationEligible = isIndiaOnly(location, text);
        if (!locationEligible) {
            reasons.add("Location: Excluded (outside India; only India & India Remote jobs are selected).");
            return new Result(0, false, null, matchedSkills, reasons, "excluded_location", newThisRun);
        }
        int locScore = 10;
        reasons.add("Location (10/10): India verified.");

        // ----------------------------------------------------
        // PILLAR 5: POSTED DATE RECENCY (10 Pts Max)
        // ----------------------------------------------------
        int freshScore = 6;
        Integer age = null;
        if (j.publishedAt() != null) {
            age = (int) Math.max(0, Duration.between(j.publishedAt(), Instant.now()).toDays());
            if (age <= 3) {
                freshScore = 10;
                reasons.add("Freshness (10/10): Posted " + age + " day(s) ago (hot opening).");
            } else if (age <= 7) {
                freshScore = 8;
                reasons.add("Freshness (8/10): Posted " + age + " days ago.");
            } else if (age <= 14) {
                freshScore = 6;
                reasons.add("Freshness (6/10): Posted " + age + " days ago.");
            } else if (age <= config.getMaxAgeDays()) {
                freshScore = 4;
                reasons.add("Freshness (4/10): Posted " + age + " days ago.");
            } else {
                freshScore = 0;
                reasons.add("Freshness (0/10): Exceeded " + config.getMaxAgeDays() + " days cutoff.");
            }
        } else {
            freshScore = 6;
            reasons.add("Freshness (6/10): Publication date not provided by ATS.");
        }

        // ----------------------------------------------------
        // COMPENSATION STATUS
        // ----------------------------------------------------
        String comp = lower(j.compensationText());
        String compStatus = "unknown";
        Matcher m = Pattern.compile("(?:₹|inr|rs\\.?\\s*)?\\s*([\\d,.]+)\\s*(?:-|to|–)\\s*(?:₹|inr|rs\\.?\\s*)?\\s*([\\d,.]+)\\s*(?:lpa|lakhs?)", Pattern.CASE_INSENSITIVE).matcher(comp);
        if (m.find()) {
            try {
                double lo = Double.parseDouble(m.group(1).replace(",", ""));
                double hi = Double.parseDouble(m.group(2).replace(",", ""));
                compStatus = hi < config.getTargetCtcLpa() ? "below_target" : lo >= config.getTargetCtcLpa() ? "meets_target" : "range_overlaps_target";
            } catch (Exception ignored) {}
        }
        reasons.add("Compensation: " + compStatus.replace('_', ' ') + ".");

        // ----------------------------------------------------
        // TOTAL SCORE & ELIGIBILITY
        // ----------------------------------------------------
        int totalScore = roleScore + skillScore + expScore + locScore + freshScore;
        totalScore = Math.min(100, Math.max(0, totalScore));

        boolean eligible = totalScore >= config.getMinScore()
                && (age == null || age <= config.getMaxAgeDays())
                && !compStatus.equals("below_target");

        return new Result(totalScore, eligible, age, matchedSkills, reasons, compStatus, newThisRun);
    }

    private static double extractMinExperience(String text) {
        double minYears = -1.0;

        // 1. Check ranges first e.g. "2-5 years of experience" -> min requirement is 2
        Matcher m3 = EXP_RANGE_PATTERN.matcher(text);
        while (m3.find()) {
            try {
                double lower = Double.parseDouble(m3.group(1));
                if (lower <= 15.0 && (minYears < 0 || lower < minYears)) {
                    minYears = lower;
                }
            } catch (Exception ignored) {}
        }

        // 2. Check explicit minimums e.g. "at least 3 years" or "at least 2.6 years"
        Matcher m2 = EXP_MIN_PATTERN.matcher(text);
        while (m2.find()) {
            try {
                double y = Double.parseDouble(m2.group(1));
                if (y <= 15.0 && (minYears < 0 || y < minYears)) {
                    minYears = y;
                }
            } catch (Exception ignored) {}
        }

        // 3. Check "2.6+ years" or "3+ years"
        Matcher m1 = EXP_PLUS_PATTERN.matcher(text);
        while (m1.find()) {
            try {
                double y = Double.parseDouble(m1.group(1));
                if (y <= 15.0 && (minYears < 0 || y < minYears)) {
                    minYears = y;
                }
            } catch (Exception ignored) {}
        }

        // 4. Check "3 years of experience"
        Matcher m4 = EXP_YEARS_PATTERN.matcher(text);
        while (m4.find()) {
            try {
                double y = Double.parseDouble(m4.group(1));
                if (y <= 15.0 && (minYears < 0 || y < minYears)) {
                    minYears = y;
                }
            } catch (Exception ignored) {}
        }

        return minYears;
    }

    private boolean isIndiaOnly(String location, String text) {
        String loc = location == null ? "" : location.toLowerCase(Locale.ROOT);
        boolean hasExplicitForeign = REGIONAL_EXCLUDES.stream().anyMatch(loc::contains);
        boolean hasIndiaHubInLoc = INDIA_HUBS.stream().anyMatch(loc::contains);

        // 1. Direct location field check
        if (hasIndiaHubInLoc && !hasExplicitForeign) {
            return true;
        }
        if (hasExplicitForeign && !hasIndiaHubInLoc) {
            return false;
        }
        if (hasIndiaHubInLoc) {
            return true;
        }

        // 2. Remote check: if explicitly marked remote or empty location, verify India-specific terms
        boolean isRemote = loc.contains("remote") || loc.isBlank();
        if (isRemote) {
            boolean hasIndiaMention = INDIA_HUBS.stream().anyMatch(h -> text.contains(" " + h + " ") || text.contains(h + ","));
            boolean hasInrComp = text.contains("inr") || text.contains("lpa") || text.contains("₹");
            boolean hasForeignExclude = REGIONAL_EXCLUDES.stream().anyMatch(ex -> loc.contains(ex) || text.contains(" " + ex + " "));

            return (hasIndiaMention || hasInrComp) && !hasForeignExclude;
        }

        return false;
    }

    private String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    public record Result(int score, boolean eligible, Integer ageDays, List<String> skills, List<String> reasons, String compensationStatus, boolean newThisRun) {}
}
