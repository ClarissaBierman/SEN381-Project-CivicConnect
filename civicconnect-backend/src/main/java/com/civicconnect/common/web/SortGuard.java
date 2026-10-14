package com.civicconnect.common.web;

import com.civicconnect.common.error.Errors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;

/**
 * Only lets a client sort by the fields an endpoint names. Without this, ?sort=anything reaches the
 * database: an unknown field was a 500, and a nested one such as requester.password produced
 * "ORDER BY password" (the database role blocked it, but only by luck of the grants).
 */
public final class SortGuard {

    private SortGuard() {}

    public static Pageable allow(Pageable pageable, String... allowedProperties) {
        Set<String> allowed = Set.of(allowedProperties);
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw Errors.invalidField("sort", "Cannot sort by '" + order.getProperty()
                        + "'. Allowed: " + String.join(", ", new TreeSet<>(allowed)) + ".");
            }
        }
        return pageable;
    }
}
