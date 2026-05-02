package com.product.application.cache;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RebuildJobTest {

    @Test
    void restore_recreatesJobFromSnapshot() {
        UUID jobId = UUID.randomUUID();
        LocalDateTime startedAt = LocalDateTime.of(2026, 5, 3, 1, 0);
        LocalDateTime finishedAt = LocalDateTime.of(2026, 5, 3, 1, 1);

        RebuildJob.Snapshot snapshot = new RebuildJob.Snapshot(
                jobId,
                10L,
                "ALL",
                startedAt,
                RebuildJobStatus.SUCCEEDED,
                10L,
                100,
                false,
                "done",
                null,
                finishedAt
        );

        RebuildJob actual = RebuildJob.restore(snapshot);

        assertThat(actual.getJobId()).isEqualTo(jobId);
        assertThat(actual.getTotal()).isEqualTo(10L);
        assertThat(actual.getFilterSummary()).isEqualTo("ALL");
        assertThat(actual.getStartedAt()).isEqualTo(startedAt);
        assertThat(actual.getStatus()).isEqualTo(RebuildJobStatus.SUCCEEDED);
        assertThat(actual.getProcessed()).isEqualTo(10L);
        assertThat(actual.getProgressPercent()).isEqualTo(100);
        assertThat(actual.isActive()).isFalse();
        assertThat(actual.getMessage()).isEqualTo("done");
        assertThat(actual.getFinishedAt()).isEqualTo(finishedAt);
    }

    @Test
    void restore_clampsProcessedToTotal() {
        RebuildJob.Snapshot snapshot = new RebuildJob.Snapshot(
                UUID.randomUUID(),
                10L,
                "ALL",
                LocalDateTime.of(2026, 5, 3, 1, 0),
                RebuildJobStatus.RUNNING,
                15L,
                100,
                true,
                "running",
                null,
                null
        );

        RebuildJob actual = RebuildJob.restore(snapshot);

        assertThat(actual.getProcessed()).isEqualTo(10L);
        assertThat(actual.getProgressPercent()).isEqualTo(100);
    }
}
