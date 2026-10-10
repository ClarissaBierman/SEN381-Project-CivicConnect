package com.civicconnect.repository;

import com.civicconnect.model.Client;
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
     * Activates or deactivates a client without loading the row, so Management never reads
     * client personal data or password hashes (ASR-005). Returns the number of rows changed.
     */
    @Modifying
    @Query("update Client c set c.active = :active, c.updatedAt = :now where c.clientId = :id")
    int updateActive(@Param("id") Long id, @Param("active") boolean active, @Param("now") LocalDateTime now);
}
