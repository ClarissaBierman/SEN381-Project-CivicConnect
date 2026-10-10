package com.civicconnect.repository;

import com.civicconnect.model.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditRepository extends JpaRepository<AuditEntry, Long> {

    /**
     * Full history of one request: changes to the request row itself, plus its comments
     * and handler assignments (those rows carry req_id in their JSON data). Oldest first.
     */
    @Query(value = """
            select * from audit_entry a
            where (a.table_name = 'request' and a.record_id = :reqId)
               or (a.table_name in ('comment', 'request_handler')
                   and cast(coalesce(a.new_data, a.old_data) as jsonb) ->> 'req_id' = :reqId)
            order by a.changed_at, a.aud_id
            """, nativeQuery = true)
    List<AuditEntry> findRequestHistory(@Param("reqId") String reqId);
}
