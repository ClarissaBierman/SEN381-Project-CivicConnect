package com.civicconnect.service;

import com.civicconnect.dto.CategoryPatch;
import com.civicconnect.dto.NewCategory;
import com.civicconnect.error.ErrorCode;
import com.civicconnect.model.Category;
import com.civicconnect.repository.CategoryRepository;
import com.civicconnect.security.CurrentUserService;
import com.civicconnect.security.DbSession;
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
        service = new CategoryService(repository, currentUser, mock(DbSession.class));
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
}
