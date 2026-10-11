package com.civicconnect.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Composite key of HandlerCategory: (emp_id, cat_id). */
@Embeddable
public class HandlerCategoryId implements Serializable {

    @Column(name = "emp_id")
    private Long empId;

    @Column(name = "cat_id")
    private Long catId;

    public HandlerCategoryId() {}

    public HandlerCategoryId(Long empId, Long catId) {
        this.empId = empId;
        this.catId = catId;
    }

    public Long getEmpId() { return empId; }
    public void setEmpId(Long empId) { this.empId = empId; }

    public Long getCatId() { return catId; }
    public void setCatId(Long catId) { this.catId = catId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HandlerCategoryId)) return false;
        HandlerCategoryId that = (HandlerCategoryId) o;
        return Objects.equals(empId, that.empId) && Objects.equals(catId, that.catId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(empId, catId);
    }
}
