package com.forge.worker.build;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BuildRepositoryTest {

    @Test
    void onlyQueuedBuildCanBeClaimedOnce() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        BuildRepository repository = new BuildRepository(jdbcTemplate);
        UUID buildId = UUID.randomUUID();

        when(jdbcTemplate.update(anyString(), eq(buildId)))
                .thenReturn(1, 0);

        assertTrue(repository.claimQueuedBuild(buildId));
        assertFalse(repository.claimQueuedBuild(buildId));

        verify(jdbcTemplate, times(2))
                .update(anyString(), eq(buildId));
    }
}