package com.civicconnect.model;

import jakarta.persistence.*;

/**
 * Links a handler (Employee) to a category they may see and work on (FR-005, DEC-002).
 * Maintained by Management.
 */
@Entity
@Table(name = "handler_category")
public class HandlerCategory {

    @EmbeddedId
    private HandlerCategoryId id = new HandlerCategoryId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("empId")
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("catId")
    @JoinColumn(name = "cat_id", nullable = false)
    private Category category;

    public HandlerCategory() {}

    public HandlerCategory(Employee employee, Category category) {
        this.employee = employee;
        this.category = category;
        this.id = new HandlerCategoryId(employee.getEmpId(), category.getCatId());
    }

    public HandlerCategoryId getId() { return id; }
    public void setId(HandlerCategoryId id) { this.id = id; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
}
