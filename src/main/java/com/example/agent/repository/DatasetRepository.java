package com.example.agent.repository;

import com.example.agent.model.entity.Dataset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class DatasetRepository {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DatasetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Dataset> datasetRowMapper = (ResultSet rs, int rowNum) -> {
        Dataset dataset = new Dataset();
        dataset.setId(rs.getLong("id"));
        dataset.setUserId(rs.getLong("user_id"));
        dataset.setFileName(rs.getString("file_name"));
        dataset.setFileType(rs.getString("file_type"));
        dataset.setTableName(rs.getString("table_name"));
        dataset.setColumnInfo(rs.getString("column_info"));
        dataset.setRowCount(rs.getLong("row_count"));
        dataset.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return dataset;
    };

    public Dataset save(Dataset dataset) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO datasets (user_id, file_name, file_type, table_name, column_info, row_count) VALUES (?, ?, ?, ?, ?, ?)",
                new String[]{"ID"}
            );
            ps.setLong(1, dataset.getUserId());
            ps.setString(2, dataset.getFileName());
            ps.setString(3, dataset.getFileType());
            ps.setString(4, dataset.getTableName());
            ps.setString(5, dataset.getColumnInfo());
            ps.setLong(6, dataset.getRowCount());
            return ps;
        }, keyHolder);
        dataset.setId(keyHolder.getKey().longValue());
        return dataset;
    }

    public List<Dataset> findByUserId(Long userId) {
        return jdbcTemplate.query(
            "SELECT * FROM datasets WHERE user_id = ? ORDER BY created_at DESC",
            datasetRowMapper, userId
        );
    }

    public Optional<Dataset> findById(Long id) {
        var list = jdbcTemplate.query(
            "SELECT * FROM datasets WHERE id = ?",
            datasetRowMapper, id
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<Dataset> findByIdAndUserId(Long id, Long userId) {
        var list = jdbcTemplate.query(
            "SELECT * FROM datasets WHERE id = ? AND user_id = ?",
            datasetRowMapper, id, userId
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM datasets WHERE id = ?", id);
    }
}
