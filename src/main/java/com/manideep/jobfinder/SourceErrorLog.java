package com.manideep.jobfinder;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "source_error_logs")
public class SourceErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 250)
    private String company;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false, length = 20)
    private String status; // FAILED, WARNING

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SourceErrorLog() {}

    public SourceErrorLog(String company, String source, String status, String errorMessage) {
        this.company = company != null ? company : "Unknown";
        this.source = source != null ? source : "unknown";
        this.status = status != null ? status : "FAILED";
        this.errorMessage = errorMessage;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCompany() { return company; }
    public String getSource() { return source; }
    public String getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }

    private static final DateTimeFormatter IST_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").withZone(ZoneId.of("Asia/Kolkata"));

    public String getFormattedCreatedAt() {
        return createdAt != null ? IST_FORMATTER.format(createdAt) : "";
    }
}
