package com.example.agent.repository;

import com.example.agent.model.entity.CleaningHistory;
import com.example.agent.model.enums.DatasetStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class CleaningHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public CleaningHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CleaningHistory save(CleaningHistory h) {
        if (h.getId() == null) {
            jdbcTemplate.update(
                "INSERT INTO cleaning_history (dataset_id, user_id, issues_json, executed_sql, affected_rows, status, error_message, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                h.getDatasetId(), h.getUserId(), h.getIssuesJson(), h.getExecutedSql(),
                h.getAffectedRows(), h.getStatus().name(), h.getErrorMessage(), h.getCreatedAt()
            );
        } else {
            jdbcTemplate.update(
                "UPDATE cleaning_history SET dataset_id=?, user_id=?, issues_json=?, executed_sql=?, affected_rows=?, status=?, error_message=?, created_at=? WHERE id=?",
                h.getDatasetId(), h.getUserId(), h.getIssuesJson(), h.getExecutedSql(),
                h.getAffectedRows(), h.getStatus().name(), h.getErrorMessage(), h.getCreatedAt(), h.getId()
            );
        }
        return h;
    }

    public List<CleaningHistory> findByDatasetIdAndUserIdOrderByCreatedAtDesc(Long datasetId, Long userId) {
        return jdbcTemplate.query(
            "SELECT * FROM cleaning_history WHERE dataset_id = ? AND user_id = ? ORDER BY created_at DESC",
            new CleaningHistoryRowMapper(), datasetId, userId
        );
    }

    private static class CleaningHistoryRowMapper implements RowMapper<CleaningHistory> {
        @Override
        public CleaningHistory mapRow(ResultSet rs, int rowNum) throws SQLException {
            CleaningHistory h = new CleaningHistory();
            h.setId(rs.getLong("id"));
            h.setDatasetId(rs.getLong("dataset_id"));
            h.setUserId(rs.getLong("user_id"));
            h.setIssuesJson(rs.getString("issues_json"));
            h.setExecutedSql(rs.getString("executed_sql"));
            h.setAffectedRows(rs.getLong("affected_rows"));
            h.setStatus(DatasetStatus.valueOf(rs.getString("status")));
            h.setErrorMessage(rs.getString("error_message"));
            h.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            return h;
        }
    }
}
