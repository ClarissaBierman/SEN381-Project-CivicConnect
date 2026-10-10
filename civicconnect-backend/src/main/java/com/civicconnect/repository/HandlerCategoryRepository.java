package com.civicconnect.repository;

import com.civicconnect.model.HandlerCategory;
import com.civicconnect.model.HandlerCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HandlerCategoryRepository extends JpaRepository<HandlerCategory, HandlerCategoryId> {

    @Query("select h from HandlerCategory h where h.id.empId = :empId")
    List<HandlerCategory> findByEmployee(@Param("empId") Long empId);

    /** Category IDs a handler may work on (FR-005). */
    @Query("select h.id.catId from HandlerCategory h where h.id.empId = :empId")
    List<Long> findCategoryIds(@Param("empId") Long empId);
}
