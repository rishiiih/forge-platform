package com.forge.worker.build;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class BuildRepository {

    private final JdbcTemplate jdbcTemplate;

    public BuildRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean claimQueuedBuild(UUID buildId) {
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE builds
                SET status = 'RUNNING',
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'QUEUED'
                """,
                buildId
        );

        return updatedRows == 1;
    }
}
