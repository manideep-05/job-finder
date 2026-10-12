package com.manideep.jobfinder;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    Optional<JobPosting> findBySourceAndExternalId(String source, String externalId);
    List<JobPosting> findByEligibleTrueOrderByMatchScoreDescLastSeenAtDesc();
    List<JobPosting> findByEligibleTrueOrderByLastRunNewDescMatchScoreDescLastSeenAtDesc();
    List<JobPosting> findByEligibleTrueAndMatchScoreGreaterThanEqualOrderByMatchScoreDescLastSeenAtDesc(int matchScore);
    List<JobPosting> findAllByOrderByLastSeenAtDesc();
    List<JobPosting> findAllByMatchScoreGreaterThanEqualOrderByLastSeenAtDesc(int matchScore);
}
