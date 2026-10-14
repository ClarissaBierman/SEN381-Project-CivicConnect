package com.civicconnect.reporting.service;

import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.security.CurrentUserService;
import com.civicconnect.common.security.DbSession;
import com.civicconnect.reporting.dto.ActivityReport;
import com.civicconnect.reporting.repository.ReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.civicconnect.support.TestData.assertApiError;
import static com.civicconnect.support.TestData.manager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** API-17: the activity counts add up per category and overall (FR-012). */
class ReportServiceTest {

    private final ReportRepository repo = mock(ReportRepository.class);
    private final ReportService service;

    ReportServiceTest() {
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.get()).thenReturn(manager(1));
        service = new ReportService(repo, currentUser, mock(DbSession.class));
    }

    @Test
    @DisplayName("Counts are grouped into open / overdue / resolved / closed / rejected, with empty categories listed")
    void countsAddUp() {
        when(repo.categories(null)).thenReturn(List.of(
                new ReportRepository.CategoryName(4, "IT Support"), new ReportRepository.CategoryName(6, "Lost Property")));
        when(repo.countByCategoryAndStatus(isNull(), isNull(), isNull(), isNull(), any())).thenReturn(List.of(
                new ReportRepository.StatusCount(4, "NEW", true, 2),
                new ReportRepository.StatusCount(4, "IN_PROGRESS", false, 1),
                new ReportRepository.StatusCount(4, "RESOLVED", false, 3),
                new ReportRepository.StatusCount(4, "REJECTED", false, 1)));

        ActivityReport r = service.activity(null, null, null, null);

        assertThat(r.totals()).isEqualTo(new ActivityReport.Counts(7, 3, 2, 3, 0, 1));
        assertThat(r.byCategory()).extracting(ActivityReport.CategoryCounts::categoryName)
                .containsExactly("IT Support", "Lost Property");
        assertThat(r.byCategory().get(1).total()).isZero();
    }

    @Test
    @DisplayName("'From' after 'To' is a 400 on the from field")
    void fromAfterTo() {
        assertApiError(ErrorCode.VALIDATION_FAILED,
                () -> service.activity(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1), null, null));
    }
}
