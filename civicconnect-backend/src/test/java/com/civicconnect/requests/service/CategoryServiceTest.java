package com.civicconnect.requests.service;

import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.security.CurrentUserService;
import com.civicconnect.requests.domain.Category;
import com.civicconnect.requests.dto.CategoryPatch;
import com.civicconnect.requests.dto.NewCategory;
import com.civicconnect.requests.repository.CategoryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.civicconnect.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** FR-015: the Management-controlled category list. */
class CategoryServiceTest {

    private CategoryRepository repository;
    private CurrentUserService currentUser;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(CategoryRepository.class);
        currentUser = mock(CurrentUserService.class);
        service = new CategoryService(repository, currentUser);
        when(repository.findByActiveTrueOrderByCatNameAsc()).thenReturn(List.of(category(1, "Facility Faults", true)));
        when(repository.findAllByOrderByCatNameAsc())
                .thenReturn(List.of(category(1, "Facility Faults", true), category(8, "Parking", false)));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Requesters only ever see active categories, even if they ask for all")
    void requesterSeesActiveOnly() {
        when(currentUser.get()).thenReturn(requester(1));

        assertThat(service.list(false)).extracting("catName").containsExactly("Facility Faults");
    }

    @Test
    @DisplayName("Managers see deactivated categories too")
    void managerSeesAll() {
        when(currentUser.get()).thenReturn(manager(1));

        assertThat(service.list(false)).hasSize(2);
    }

    @Test
    @DisplayName("Category names must be unique, ignoring case")
    void duplicateNameRejected() {
        when(currentUser.get()).thenReturn(manager(1));
        when(repository.existsByCatNameIgnoreCase("it support")).thenReturn(true);

        assertApiError(ErrorCode.CONFLICT, () -> service.create(new NewCategory(" it support ")));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deactivating a category keeps it (it is never deleted)")
    void deactivateKeepsCategory() {
        when(currentUser.get()).thenReturn(manager(1));
        Category parking = category(8, "Parking", true);
        when(repository.findById(8L)).thenReturn(Optional.of(parking));

        var response = service.update(8L, new CategoryPatch(null, false));

        assertThat(response.active()).isFalse();
        assertThat(parking.getCatName()).isEqualTo("Parking");
        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("Create, get, rename and the duplicate-name check on rename")
    void createGetRename() {
        when(currentUser.get()).thenReturn(manager(1));
        when(repository.findById(1L)).thenReturn(Optional.of(category(1, "Facility Faults", true)));
        when(repository.existsByCatNameIgnoreCase("Parking")).thenReturn(true);

        assertThat(service.create(new NewCategory("  Roads ")).catName()).isEqualTo("Roads");
        assertThat(service.get(1L).catName()).isEqualTo("Facility Faults");
        assertThat(service.update(1L, new CategoryPatch(" FACILITY faults ", null)).catName()).isEqualTo("FACILITY faults");
        assertApiError(ErrorCode.CONFLICT, () -> service.update(1L, new CategoryPatch("Parking", null)));
        assertApiError(ErrorCode.NOT_FOUND, () -> service.get(9L));
        assertApiError(ErrorCode.NOT_FOUND, () -> service.update(9L, new CategoryPatch("X", null)));
    }

    @Test
    @DisplayName("Staff asking for active categories only get active ones")
    void activeOnlyForStaff() {
        when(currentUser.get()).thenReturn(staff(3));
        assertThat(service.list(true)).hasSize(1);
    }

    @Test
    @DisplayName("A requester cannot open an inactive category by ID; staff and managers can")
    void inactiveCategoryHiddenFromRequesters() {
        when(repository.findById(8L)).thenReturn(Optional.of(category(8, "Parking", false)));

        when(currentUser.get()).thenReturn(requester(1));
        assertApiError(ErrorCode.NOT_FOUND, () -> service.get(8L));

        when(currentUser.get()).thenReturn(manager(1));
        assertThat(service.get(8L).active()).isFalse();
    }
}
