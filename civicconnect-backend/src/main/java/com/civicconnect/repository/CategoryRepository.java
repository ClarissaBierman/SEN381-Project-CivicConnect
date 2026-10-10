package com.civicconnect.repository;

import com.civicconnect.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByCatNameAsc();

    List<Category> findByActiveTrueOrderByCatNameAsc();

    boolean existsByCatNameIgnoreCase(String catName);
}
