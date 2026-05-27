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

    /** Owner-scoped fetch incl. soft-deleted (for delete/restore). Empty if not owner. */
    Optional<LoggedEvent> findByIdAndUserId(UUID id, UUID userId);

    /** Owner-scoped fetch of a LIVE entry (reads/edits). Empty if missing or soft-deleted. */
    Optional<LoggedEvent> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

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
              and e.deletedAt is null
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
              and e.deletedAt is null
              and e.eventTypeId in :eventTypeIds
              and e.occurredAt >= :from and e.occurredAt < :to
            """)
    long countScopedForTypes(@Param("userId") UUID userId,
                             @Param("eventTypeIds") List<UUID> eventTypeIds,
                             @Param("from") OffsetDateTime from,
                             @Param("to") OffsetDateTime to);

    // --- Stats aggregates (live rows only). Day buckets are caller-tz. ---

    /** [event_type_id text, count] for the user's live entries in [from, to). */
    @Query(value = """
            select event_type_id::text, count(*)
            from logged_events
            where user_id = :userId and deleted_at is null
              and occurred_at >= :from and occurred_at < :to
            group by event_type_id
            """, nativeQuery = true)
    List<Object[]> countByType(@Param("userId") UUID userId,
                               @Param("from") OffsetDateTime from,
                               @Param("to") OffsetDateTime to);

    /**
     * [day 'YYYY-MM-DD' in {@code tz}, count] for the user's live entries in
     * [from, to). {@code tz} is an IANA zone (e.g. "America/Toronto"); Postgres
     * does the offset arithmetic via {@code at time zone}, so the bucket
     * boundaries line up with the user's local midnight rather than UTC.
     */
    @Query(value = """
            select to_char(occurred_at at time zone :tz, 'YYYY-MM-DD'), count(*)
            from logged_events
            where user_id = :userId and deleted_at is null
              and occurred_at >= :from and occurred_at < :to
            group by 1 order by 1
            """, nativeQuery = true)
    List<Object[]> countByDay(@Param("userId") UUID userId,
                              @Param("from") OffsetDateTime from,
                              @Param("to") OffsetDateTime to,
                              @Param("tz") String tz);
}
