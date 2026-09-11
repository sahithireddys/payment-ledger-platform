package com.sahithireddy.paymentledger.repository;

import com.sahithireddy.paymentledger.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Fetches a batch of unpublished events, locking them with a native
     * {@code FOR UPDATE SKIP LOCKED}. If this service is ever scaled to
     * multiple instances, each publisher poll picks disjoint rows instead of
     * blocking on (or double-publishing) the others' in-flight batch.
     */
    @Query(
        value = """
            select * from outbox_events
            where published_at is null
            order by created_at asc
            limit :limit
            for update skip locked
            """,
        nativeQuery = true)
    List<OutboxEvent> findUnpublishedBatchForUpdate(@Param("limit") int limit);
}
