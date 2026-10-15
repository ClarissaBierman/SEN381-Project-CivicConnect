package com.civicconnect.requests.repository;

import com.civicconnect.requests.domain.RequestHandler;
import com.civicconnect.requests.domain.ServiceRequest;
import com.civicconnect.requests.domain.ServiceRequest.Priority;
import com.civicconnect.requests.domain.ServiceRequest.Status;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Filters and ordering for request lists (API-05, API-07). */
public final class RequestSpecs {

    private RequestSpecs() {}

    public static Specification<ServiceRequest> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<ServiceRequest> requester(Long clientId) {
        return (root, query, cb) -> cb.equal(root.get("requester").get("clientId"), clientId);
    }

    public static Specification<ServiceRequest> status(Status status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** New, Assigned or In Progress. */
    public static Specification<ServiceRequest> open() {
        return (root, query, cb) -> root.get("status").in(ServiceRequest.OPEN_STATUSES);
    }

    /** No current handler. */
    public static Specification<ServiceRequest> unassigned() {
        return (root, query, cb) -> {
            Subquery<Long> active = query.subquery(Long.class);
            Root<RequestHandler> rh = active.from(RequestHandler.class);
            active.select(rh.get("reqHandlerId"))
                    .where(cb.equal(rh.get("request"), root), cb.isNull(rh.get("unassignedAt")));
            return cb.not(cb.exists(active));
        };
    }

    public static Specification<ServiceRequest> category(Long categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("catId"), categoryId);
    }

    public static Specification<ServiceRequest> categoryIn(Collection<Long> categoryIds) {
        return (root, query, cb) -> root.get("category").get("catId").in(categoryIds);
    }

    /** Created on or after this UTC day. */
    public static Specification<ServiceRequest> createdFrom(LocalDate from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay());
    }

    /** Created on or before this UTC day. */
    public static Specification<ServiceRequest> createdTo(LocalDate to) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay());
    }

    /** A spec plus the page to run it with; see {@link #ordered}. */
    public record Query(Specification<ServiceRequest> spec, Pageable pageable) {}

    /**
     * Priority is stored as text, so a plain ORDER BY puts it in alphabetical order (HIGH, LOW, MEDIUM)
     * with unset first. When the sort includes priority, the ORDER BY is built here instead:
     * High > Medium > Low for "desc" (Low > Medium > High for "asc"), unset always last, then the other
     * sort fields, then newest first. Other sorts are left to Spring Data unchanged.
     */
    public static Query ordered(Specification<ServiceRequest> spec, Pageable pageable) {
        Sort sort = pageable.getSort();
        if (sort.getOrderFor("priority") == null) {
            return new Query(spec, pageable);
        }
        Specification<ServiceRequest> withOrder = spec.and((root, query, cb) -> {
            // The count query of a page must not be ordered
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                query.orderBy(orders(sort, root, cb));
            }
            return cb.conjunction();
        });
        return new Query(withOrder, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
    }

    private static List<Order> orders(Sort sort, Root<ServiceRequest> root, CriteriaBuilder cb) {
        List<Order> orders = new ArrayList<>();
        boolean hasCreatedAt = false;
        for (Sort.Order o : sort) {
            Expression<?> key;
            if (o.getProperty().equals("priority")) {
                key = cb.<Integer>selectCase()
                        .when(cb.equal(root.get("priority"), Priority.HIGH), 3)
                        .when(cb.equal(root.get("priority"), Priority.MEDIUM), 2)
                        .when(cb.equal(root.get("priority"), Priority.LOW), 1)
                        .otherwise(o.isAscending() ? 4 : 0);
            } else {
                key = root.get(o.getProperty());
                hasCreatedAt |= o.getProperty().equals("createdAt");
            }
            orders.add(o.isAscending() ? cb.asc(key) : cb.desc(key));
        }
        if (!hasCreatedAt) {
            orders.add(cb.desc(root.get("createdAt")));
        }
        return orders;
    }
}
