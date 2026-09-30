package com.civicconnect.model;

import jakarta.persistence.*;

@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cat_id")
    private Long catId;

    @Column(name = "cat_name", nullable = false, unique = true, length = 100)
    private String catName;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Category() {}

    public Long getCatId() { return catId; }
    public void setCatId(Long catId) { this.catId = catId; }

    public String getCatName() { return catName; }
    public void setCatName(String catName) { this.catName = catName; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}