package com.civicconnect.repository;

import com.civicconnect.model.HandlerCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HandlerCategoryRepository extends JpaRepository<HandlerCategory, Long> {

    @Query("select h from HandlerCategory h where h.employee.empId = :empId")
    List<HandlerCategory> findByEmployee(@Param("empId") Long empId);

    /** Category IDs a handler may work on (FR-005). */
    @Query("select h.category.catId from HandlerCategory h where h.employee.empId = :empId")
    List<Long> findCategoryIds(@Param("empId") Long empId);
}
