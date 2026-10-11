package com.civicconnect.model;

import jakarta.persistence.*;

/**
 * Links a handler (Employee) to a category they may see and work on (FR-005, DEC-002).
 * Maintained by Management. Each (employee, category) pair can only exist once.
 */
@Entity
@Table(name = "handler_category",
        uniqueConstraints = @UniqueConstraint(name = "uq_handler_category", columnNames = {"emp_id", "cat_id"}))
public class HandlerCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "handler_category_id")
    private Long handlerCategoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cat_id", nullable = false)
    private Category category;

    public HandlerCategory() {}

    public HandlerCategory(Employee employee, Category category) {
        this.employee = employee;
        this.category = category;
    }

    public Long getHandlerCategoryId() { return handlerCategoryId; }
    public void setHandlerCategoryId(Long handlerCategoryId) { this.handlerCategoryId = handlerCategoryId; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
}
