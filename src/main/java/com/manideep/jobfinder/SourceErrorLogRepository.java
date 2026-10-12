package com.manideep.jobfinder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface SourceErrorLogRepository extends JpaRepository<SourceErrorLog, Long> {

    List<SourceErrorLog> findTop100ByOrderByCreatedAtDesc();

    @Modifying
    @Transactional
    @Query("DELETE FROM SourceErrorLog l WHERE l.createdAt < :cutoff")
    int deleteLogsOlderThan(@Param("cutoff") Instant cutoff);
}
