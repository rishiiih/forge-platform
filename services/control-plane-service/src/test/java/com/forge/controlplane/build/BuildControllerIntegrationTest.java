package com.forge.controlplane.build;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class BuildControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateBuildAndPersistBuildAndOutboxEvent() {

        UUID organizationId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        String repository =
                "https://github.com/example/demo";

        String commitSha =
                "abc123def456";

        String branch =
                "main";

        String requestBody = """
                {
                    "organizationId": "%s",
                    "projectId": "%s",
                    "repository": "%s",
                    "commitSha": "%s",
                    "branch": "%s"
                }
                """.formatted(
                organizationId,
                projectId,
                repository,
                commitSha,
                branch
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request =
                new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/api/v1/builds",
                        request,
                        String.class
                );

        // HTTP contract
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        assertThat(response.getBody())
                .isNotNull()
                .contains("\"buildId\"")
                .contains("\"status\":\"QUEUED\"");

        // Extract build ID from response.
        String responseBody = response.getBody();

        String buildIdString =
                responseBody
                        .replaceAll(
                                ".*\"buildId\":\"([^\"]+)\".*",
                                "$1"
                        );

        UUID buildId = UUID.fromString(buildIdString);

        // Verify build row.
        Integer buildCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM builds
                        WHERE id = ?
                          AND organization_id = ?
                          AND project_id = ?
                          AND commit_sha = ?
                          AND branch = ?
                          AND status = ?
                        """,
                        Integer.class,
                        buildId,
                        organizationId,
                        projectId,
                        commitSha,
                        branch,
                        BuildStatus.QUEUED.name()
                );

        assertThat(buildCount)
                .isEqualTo(1);

        // Verify outbox event.
        Integer outboxCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM outbox_events
                        WHERE aggregate_id = ?
                          AND event_type = ?
                          AND event_version = ?
                          AND organization_id = ?
                          AND published_at IS NULL
                        """,
                        Integer.class,
                        buildId,
                        "BuildRequested",
                        1,
                        organizationId
                );

        assertThat(outboxCount)
                .isEqualTo(1);
    }
}