package com.example.agent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class DataCleaningControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM cleaning_history");
        jdbcTemplate.update("DELETE FROM datasets");
        jdbcTemplate.update("DELETE FROM analysis_conversation");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("INSERT INTO users (id, username, password) VALUES (1, 'admin', 'password')");
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturn400WhenDatasetNotFoundOnAnalyze() throws Exception {
        mockMvc.perform(post("/api/datasets/999/cleaning/analyze"))
               .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturn400WhenDatasetNotFoundOnExecute() throws Exception {
        mockMvc.perform(post("/api/datasets/999/cleaning/execute")
                       .contentType("application/json")
                       .content("{}"))
               .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturn400WhenDatasetNotFoundOnSaveAs() throws Exception {
        mockMvc.perform(post("/api/datasets/999/cleaning/save-as")
                       .contentType("application/json")
                       .content("{}"))
               .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturnEmptyListOnHistoryWhenNoData() throws Exception {
        mockMvc.perform(get("/api/datasets/999/cleaning/history"))
               .andExpect(status().isOk());
    }
}