package com.civicconnect.requests.repository;

import com.civicconnect.requests.domain.ServiceRequest;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RequestSpecsTest {

    @Test
    @DisplayName("Sorts without priority are left to Spring Data unchanged")
    void otherSortsUnchanged() {
        PageRequest page = PageRequest.of(2, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        RequestSpecs.Query q = RequestSpecs.ordered(RequestSpecs.all(), page);
        assertThat(q.pageable()).isSameAs(page);
    }

    @Test
    @DisplayName("A priority sort is built as a CASE order (unset last), and the count query is not ordered")
    @SuppressWarnings("unchecked")
    void prioritySortUsesCase() {
        RequestSpecs.Query q = RequestSpecs.ordered(RequestSpecs.all(),
                PageRequest.of(1, 20, Sort.by(Sort.Direction.DESC, "priority")));
        assertThat(q.pageable().getSort().isSorted()).isFalse();
        assertThat(q.pageable().getPageNumber()).isEqualTo(1);

        Root<ServiceRequest> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        CriteriaQuery<ServiceRequest> select = mock(CriteriaQuery.class);
        doReturn(ServiceRequest.class).when(select).getResultType();
        q.spec().toPredicate(root, select, cb);
        verify(select).orderBy(anyList());
        verify(cb.selectCase().when(any(), eq(3)).when(any(), eq(2)).when(any(), eq(1))).otherwise(0);

        CriteriaQuery<Long> count = mock(CriteriaQuery.class);
        doReturn(Long.class).when(count).getResultType();
        q.spec().toPredicate(root, count, cb);
        verify(count, never()).orderBy(anyList());
    }

    @Test
    @DisplayName("Ascending priority still puts unset last")
    @SuppressWarnings("unchecked")
    void ascendingPutsUnsetLast() {
        RequestSpecs.Query q = RequestSpecs.ordered(RequestSpecs.all(),
                PageRequest.of(0, 20, Sort.by(Sort.Order.asc("priority"), Sort.Order.asc("createdAt"))));
        Root<ServiceRequest> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        CriteriaQuery<ServiceRequest> select = mock(CriteriaQuery.class);
        doReturn(ServiceRequest.class).when(select).getResultType();
        q.spec().toPredicate(root, select, cb);
        verify(cb.selectCase().when(any(), eq(3)).when(any(), eq(2)).when(any(), eq(1))).otherwise(4);
        verify(select).orderBy((List) argThat(l -> ((List<?>) l).size() == 2));
    }
}
