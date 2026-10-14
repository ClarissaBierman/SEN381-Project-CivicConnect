package com.civicconnect.access.repository;

import com.civicconnect.access.domain.HandlerCategory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HandlerCategoryRepository extends JpaRepository<HandlerCategory, Long> {

    @Query("select h from HandlerCategory h where h.employee.empId = :empId")
    List<HandlerCategory> findByEmployee(@Param("empId") Long empId);

    @Query("select h.category.catId from HandlerCategory h where h.employee.empId = :empId")
    List<Long> findCategoryIds(@Param("empId") Long empId);
}
