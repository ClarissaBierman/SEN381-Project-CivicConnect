package com.civicconnect.reporting.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportRepositoryTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final ReportRepository repository = new ReportRepository(jdbc);

    @Test
    @DisplayName("Only the filters given are added to the SQL, always as parameters")
    @SuppressWarnings("unchecked")
    void filtersAreParameters() throws Exception {
        LocalDateTime cut = LocalDateTime.of(2026, 10, 7, 0, 0);
        repository.countByCategoryAndStatus(null, null, null, null, cut);
        repository.countByCategoryAndStatus(cut.minusDays(30), cut, 4L, "NEW", cut);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        ArgumentCaptor<RowMapper<ReportRepository.StatusCount>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc, times(2)).query(sql.capture(), params.capture(), mapper.capture());
        assertThat(sql.getAllValues().get(0)).doesNotContain(":categoryId", ":status ", ":createdFrom");
        assertThat(sql.getAllValues().get(1)).contains(":createdFrom", ":createdBefore", ":categoryId", ":status");
        assertThat(params.getAllValues().get(1).getValue("status")).isEqualTo("NEW");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("cat_id")).thenReturn(4L);
        when(rs.getString("status")).thenReturn("NEW");
        when(rs.getBoolean("overdue")).thenReturn(true);
        when(rs.getLong("n")).thenReturn(2L);
        assertThat(mapper.getValue().mapRow(rs, 0)).isEqualTo(new ReportRepository.StatusCount(4, "NEW", true, 2));
    }

    @Test
    @DisplayName("Category names, optionally for one category")
    @SuppressWarnings("unchecked")
    void categories() throws Exception {
        repository.categories(null);
        repository.categories(4L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RowMapper<ReportRepository.CategoryName>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc, times(2)).query(sql.capture(), any(MapSqlParameterSource.class), mapper.capture());
        assertThat(sql.getAllValues().get(0)).doesNotContain("where");
        assertThat(sql.getAllValues().get(1)).contains("where cat_id = :categoryId");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("cat_id")).thenReturn(4L);
        when(rs.getString("cat_name")).thenReturn("IT Support");
        assertThat(mapper.getValue().mapRow(rs, 0)).isEqualTo(new ReportRepository.CategoryName(4, "IT Support"));
    }
}
