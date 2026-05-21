package com.biffis.tracker.repository;

import com.biffis.tracker.model.LoggedEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every method is user-scoped. There is intentionally NO findAll-style query
 * exposed for logged events — see CLAUDE.md / DATA_MODEL.md. Adding one is a
 * privacy bug.
 */
public interface LoggedEventRepository extends JpaRepository<LoggedEvent, UUID> {

    /** Owner-scoped single fetch. Returns empty if the id belongs to another user. */
    Optional<LoggedEvent> findByIdAndUserId(UUID id, UUID userId);

    /** Owner-scoped delete; returns rows affected (0 if not owner / not found). */
    long deleteByIdAndUserId(UUID id, UUID userId);

    /**
     * Owner-scoped window query, newest first. {@code eventTypeId} optional
     * (null = all trackers). Keyset cursor: pass {@code curTs}/{@code curId}
     * (the last row of the previous page) to fetch strictly-older rows;
     * pass null/null for the first page. Ordered by (occurredAt, id) desc so
     * the cursor is stable even when timestamps tie. {@code Pageable} caps size.
     */
    @Query("""
            select e from LoggedEvent e
            where e.userId = :userId
              and e.occurredAt >= :from and e.occurredAt < :to
              and (:eventTypeId is null or e.eventTypeId = :eventTypeId)
              and (:noCursor = true
                   or e.occurredAt < :curTs
                   or (e.occurredAt = :curTs and e.id < :curId))
            order by e.occurredAt desc, e.id desc
            """)
    List<LoggedEvent> findScoped(@Param("userId") UUID userId,
                                 @Param("from") OffsetDateTime from,
                                 @Param("to") OffsetDateTime to,
                                 @Param("eventTypeId") UUID eventTypeId,
                                 @Param("noCursor") boolean noCursor,
                                 @Param("curTs") OffsetDateTime curTs,
                                 @Param("curId") UUID curId,
                                 Pageable pageable);

    /** Count this user's events for a set of event types within a window (for home hero). */
    @Query("""
            select count(e) from LoggedEvent e
            where e.userId = :userId
              and e.eventTypeId in :eventTypeIds
              and e.occurredAt >= :from and e.occurredAt < :to
            """)
    long countScopedForTypes(@Param("userId") UUID userId,
                             @Param("eventTypeIds") List<UUID> eventTypeIds,
                             @Param("from") OffsetDateTime from,
                             @Param("to") OffsetDateTime to);
}
