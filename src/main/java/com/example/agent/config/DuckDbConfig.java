package com.example.agent.config;

import com.example.agent.service.DuckDbConnectionPool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class DuckDbConfig {

    @Value("${app.duckdb.storage-path}")
    private String storagePath;

    @Bean
    public DuckDbConnectionPool duckDbConnectionPool() {
        File dir = new File(storagePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new DuckDbConnectionPool(storagePath);
    }
}
