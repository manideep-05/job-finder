package com.manideep.jobfinder;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "job_postings", uniqueConstraints = @UniqueConstraint(name = "uk_job_source_external", columnNames = {"source", "external_id"}))
public class JobPosting {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40) private String source;
    @Column(name = "external_id", nullable = false, length = 300) private String externalId;
    @Column(nullable = false, length = 250) private String company;
    @Column(nullable = false, length = 500) private String title;
    @Column(length = 300) private String location;
    @Column(name = "job_url", nullable = false, length = 1500) private String jobUrl;
    @Column(columnDefinition = "text") private String description;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "first_seen_at", nullable = false) private Instant firstSeenAt;
    @Column(name = "last_seen_at", nullable = false) private Instant lastSeenAt;
    @Column(name = "match_score", nullable = false) private int matchScore;
    @Column(name = "compensation_status", length = 40) private String compensationStatus;
    @Column(name = "compensation_text", length = 500) private String compensationText;
    @Column(name = "is_eligible", nullable = false) private boolean eligible;
    @Column(name = "last_run_new", nullable = false) private boolean lastRunNew;
    @Column(name = "category", length = 30) private String category = "PENDING";

    protected JobPosting() {
        this.category = "PENDING";
    }

    private static String limit(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    public JobPosting(String source, String externalId, String company, String title, String location,
                      String jobUrl, String description, Instant publishedAt, Instant now,
                      int matchScore, String compensationStatus, String compensationText,
                      boolean eligible, boolean lastRunNew) {
        this.source=limit(source, 40); this.externalId=limit(externalId, 300); this.company=limit(company, 250); this.title=limit(title, 500);
        this.location=limit(location, 300); this.jobUrl=limit(jobUrl, 1500); this.description=description; this.publishedAt=publishedAt;
        this.firstSeenAt=now; this.lastSeenAt=now; this.matchScore=matchScore;
        this.compensationStatus=limit(compensationStatus, 40); this.compensationText=limit(compensationText, 500);
        this.eligible=eligible; this.lastRunNew=lastRunNew;
        this.category="PENDING";
    }

    public void refresh(String title, String location, String url, String description, Instant publishedAt,
                        Instant now, int score, String compStatus, String compText, boolean eligible, boolean isNew) {
        this.title=limit(title, 500); this.location=limit(location, 300); this.jobUrl=limit(url, 1500); this.description=description;
        if (publishedAt != null) this.publishedAt=publishedAt;
        this.lastSeenAt=now; this.matchScore=score; this.compensationStatus=limit(compStatus, 40);
        this.compensationText=limit(compText, 500); this.eligible=eligible; this.lastRunNew=isNew;
    }
    public Long getId(){return id;} public String getSource(){return source;} public String getExternalId(){return externalId;}
    public String getCompany(){return company;} public String getTitle(){return title;} public String getLocation(){return location;}
    public String getJobUrl(){return jobUrl;} public String getDescription(){return description;} public Instant getPublishedAt(){return publishedAt;}
    public Instant getFirstSeenAt(){return firstSeenAt;} public Instant getLastSeenAt(){return lastSeenAt;}
    public int getMatchScore(){return matchScore;} public String getCompensationStatus(){return compensationStatus;}
    public String getCompensationText(){return compensationText;} public boolean isEligible(){return eligible;} public boolean isLastRunNew(){return lastRunNew;}
    public String getCategory() {
        if (category == null || category.isBlank()) {
            return "PENDING";
        }
        return category.toUpperCase();
    }
    public void setCategory(String category) {
        if (category == null || category.isBlank()) {
            this.category = "PENDING";
        } else {
            String norm = category.trim().toUpperCase().replace("-", "_").replace(" ", "_");
            if (norm.equals("APPLIED")) {
                this.category = "APPLIED";
            } else if (norm.equals("NOT_RELEVANT") || norm.equals("NOT_RELAVANT") || norm.equals("NOTRELEVANT")) {
                this.category = "NOT_RELEVANT";
            } else {
                this.category = "PENDING";
            }
        }
    }

    private static final java.time.format.DateTimeFormatter FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("dd MMM, hh:mm a").withZone(java.time.ZoneId.of("Asia/Kolkata"));
    private static final java.time.format.DateTimeFormatter DATE_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(java.time.ZoneId.of("Asia/Kolkata"));

    public String getFormattedFirstSeen() { return firstSeenAt != null ? FORMATTER.format(firstSeenAt) : ""; }
    public String getFormattedLastSeen() { return lastSeenAt != null ? FORMATTER.format(lastSeenAt) : ""; }
    public String getFormattedPublishedAt() { return publishedAt != null ? DATE_FORMATTER.format(publishedAt) : ""; }
}
