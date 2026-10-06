package com.forge.controlplane.build;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
public class BuildRepository {

    private final JdbcTemplate jdbcTemplate;

    public BuildRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Build build) {

        jdbcTemplate.update(
                """
                INSERT INTO builds (
                    id,
                    organization_id,
                    project_id,
                    commit_sha,
                    branch,
                    dockerfile_path,
                    build_context,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                build.id(),
                build.organizationId(),
                build.projectId(),
                build.commitSha(),
                build.branch(),
                build.dockerfilePath(),
                build.buildContext(),
                build.status().name(),
                Timestamp.from(build.createdAt()),
                Timestamp.from(build.updatedAt())
        );
    }
}