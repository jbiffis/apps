package com.biffis.tracker.repository;

import com.biffis.tracker.model.LoggedEventOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoggedEventOptionRepository extends JpaRepository<LoggedEventOption, UUID> {

    List<LoggedEventOption> findByLoggedEventIdIn(Collection<UUID> loggedEventIds);

    /** Clear an entry's options before re-creating them on update. */
    void deleteByLoggedEventId(UUID loggedEventId);

    /**
     * The most-recently-logged measurement value for one of the caller's
     * single-measurement trackers (e.g. {@code weight}, {@code height}),
     * returned as the jsonb text (a bare number like {@code "70.5"}). Empty if
     * the user has never logged that tracker. User-scoped and ignores
     * soft-deleted entries. Drives the derived biometrics — never stored.
     */
    @Query(value = """
            select o.value #>> '{}'
            from logged_event_options o
            join logged_events e on e.id = o.logged_event_id
            join event_types  t on t.id = e.event_type_id
            where e.user_id = :userId
              and e.deleted_at is null
              and t.slug = :typeSlug
            order by e.occurred_at desc, e.id desc
            limit 1
            """, nativeQuery = true)
    Optional<String> findLatestNumericValue(@Param("userId") UUID userId,
                                            @Param("typeSlug") String typeSlug);
}
