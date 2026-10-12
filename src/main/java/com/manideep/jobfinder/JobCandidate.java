package com.manideep.jobfinder;

import java.time.Instant;

public record JobCandidate(String source, String externalId, String company, String title, String location,
                           String jobUrl, String description, Instant publishedAt, String compensationText) {}
