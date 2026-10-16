package com.civicconnect.access.repository;

import com.civicconnect.access.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Whether any account (client or employee) already uses this email address. Runs the V6 database
     * function, which may read both tables whatever the caller's database role is allowed to read
     * (a requester's role cannot read the employee table).
     */
    @Query(value = "select civicconnect.email_in_use(:email)", nativeQuery = true)
    boolean emailInUse(@Param("email") String email);

    /**
     * Activates or deactivates a client without loading the row, so Management never reads
     * client personal data or password hashes (ASR-005). Returns the number of rows changed.
     */
    @Modifying
    @Query("update Client c set c.active = :active, c.updatedAt = :now where c.clientId = :id")
    int updateActive(@Param("id") Long id, @Param("active") boolean active, @Param("now") LocalDateTime now);

    /** Active flag only, for the per-request account check (B1). */
    @Query("select c.active from Client c where c.clientId = :id")
    Optional<Boolean> findActive(@Param("id") Long id);

    /**
     * Contact details only - the columns Management may read (V3 grants). Never loads the whole row,
     * so the password hash and date of birth are not read.
     */
    @Query("""
            select c.clientId as id, c.firstName as firstName, c.surname as surname,
                   c.email as email, c.phone as phone, c.active as active
            from Client c where c.clientId = :id""")
    Optional<ClientContact> findContact(@Param("id") Long id);

    interface ClientContact {
        Long getId();
        String getFirstName();
        String getSurname();
        String getEmail();
        String getPhone();
        boolean getActive();
    }
}
