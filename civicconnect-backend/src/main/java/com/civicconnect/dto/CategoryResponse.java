package com.civicconnect.dto;

import com.civicconnect.model.Category;

public record CategoryResponse(Long id, String catName, boolean active) {

    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getCatId(), c.getCatName(), c.isActive());
    }
}
