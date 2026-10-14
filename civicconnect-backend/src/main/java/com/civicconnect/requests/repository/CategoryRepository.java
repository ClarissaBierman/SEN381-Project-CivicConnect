package com.civicconnect.requests.repository;

import com.civicconnect.requests.domain.Category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByCatNameAsc();

    List<Category> findByActiveTrueOrderByCatNameAsc();

    boolean existsByCatNameIgnoreCase(String catName);
}
