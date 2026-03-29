package infrastructure.cache;

import com.product.application.cache.RebuildJob;
import com.product.infrastructure.cache.InMemoryRebuildJobStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRebuildJobStoreTest {

    private final InMemoryRebuildJobStore store = new InMemoryRebuildJobStore();

    @Test
    @DisplayName("createIfAbsentActive 후 find 하면 동일한 job 을 조회할 수 있다")
    void createIfAbsentActiveAndFind_returnsSameJob() {
        RebuildJob created = store.createIfAbsentActive("brand=APPLE", 10L)
                .orElseThrow();

        assertThat(store.find(created.getJobId())).isPresent();
        assertThat(store.find(created.getJobId()).get()).isSameAs(created);
        assertThat(store.find(created.getJobId()).get().getJobId()).isEqualTo(created.getJobId());
        assertThat(store.find(created.getJobId()).get().getStatus().name()).isEqualTo("QUEUED");
    }

    @Test
    @DisplayName("존재하지 않는 jobId 로 조회하면 empty 를 반환한다")
    void find_returnsEmpty_whenJobDoesNotExist() {
        assertThat(store.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("활성 job 이 없으면 findActiveJob 은 empty 를 반환한다")
    void findActiveJob_returnsEmpty_whenNoActiveJob() {
        assertThat(store.findActiveJob()).isEmpty();
    }

    @Test
    @DisplayName("활성 job 이 있으면 findActiveJob 으로 조회할 수 있다")
    void findActiveJob_returnsActiveJob() {
        RebuildJob created = store.createIfAbsentActive("brand=APPLE", 10L)
                .orElseThrow();

        Optional<RebuildJob> actual = store.findActiveJob();

        assertThat(actual).isPresent();
        assertThat(actual.get()).isSameAs(created);
        assertThat(actual.get().getJobId()).isEqualTo(created.getJobId());
    }

    @Test
    @DisplayName("이미 활성 job 이 있으면 createIfAbsentActive 는 empty 를 반환한다")
    void createIfAbsentActive_returnsEmpty_whenActiveJobExists() {
        RebuildJob first = store.createIfAbsentActive("brand=APPLE", 10L)
                .orElseThrow();

        Optional<RebuildJob> second = store.createIfAbsentActive("brand=SAMSUNG", 20L);

        assertThat(second).isEmpty();
        assertThat(store.find(first.getJobId())).isPresent();
        assertThat(store.findActiveJob()).isPresent();
        assertThat(store.findActiveJob().get().getJobId()).isEqualTo(first.getJobId());
    }

    @Test
    @DisplayName("완료된 job 이 있으면 새 활성 job 을 다시 생성할 수 있다")
    void createIfAbsentActive_canCreateNewJob_whenPreviousJobCompleted() {
        RebuildJob first = store.createIfAbsentActive("brand=APPLE", 10L)
                .orElseThrow();

        store.markSucceeded(first.getJobId(), "done");

        Optional<RebuildJob> second = store.createIfAbsentActive("brand=SAMSUNG", 20L);

        assertThat(second).isPresent();
        assertThat(second.get().getJobId()).isNotEqualTo(first.getJobId());
        assertThat(store.findActiveJob()).isPresent();
        assertThat(store.findActiveJob().get().getJobId()).isEqualTo(second.get().getJobId());
    }

    @Test
    @DisplayName("markRunning 호출 시 RUNNING 상태가 된다")
    void markRunning_updatesStatusToRunning() {
        RebuildJob created = store.createIfAbsentActive("brand=APPLE", 10L)
                .orElseThrow();

        store.markRunning(created.getJobId(), "running");

        RebuildJob actual = store.find(created.getJobId()).orElseThrow();
        assertThat(actual.getStatus().name()).isEqualTo("RUNNING");
        assertThat(actual.getMessage()).isEqualTo("running");
        assertThat(actual.isActive()).isTrue();
    }

    @Test
    @DisplayName("updateProgress 호출 시 processed 와 message 가 반영된다")
    void updateProgress_updatesProcessedAndMessage() {
        RebuildJob created = store.createIfAbsentActive("brand=APPLE", 10L)
                .orElseThrow();

        store.updateProgress(created.getJobId(), 3L, "chunk 1 done");

        RebuildJob actual = store.find(created.getJobId()).orElseThrow();
        assertThat(actual.getProcessed()).isEqualTo(3L);
        assertThat(actual.getMessage()).isEqualTo("chunk 1 done");
        assertThat(actual.getProgressPercent()).isEqualTo(30);
    }

    @Test
    @DisplayName("markSucceeded 호출 시 SUCCEEDED 상태가 된다")
    void markSucceeded_marksJobSucceeded() {
        RebuildJob created = store.createIfAbsentActive("brand=APPLE", 10L)
                .orElseThrow();

        store.markSucceeded(created.getJobId(), "done");

        RebuildJob actual = store.find(created.getJobId()).orElseThrow();
        assertThat(actual.getStatus().name()).isEqualTo("SUCCEEDED");
        assertThat(actual.getProcessed()).isEqualTo(10L);
        assertThat(actual.getMessage()).isEqualTo("done");
        assertThat(actual.getFailureReason()).isNull();
        assertThat(actual.getFinishedAt()).isNotNull();
        assertThat(actual.isActive()).isFalse();
    }

    @Test
    @DisplayName("markFailed 호출 시 FAILED 상태가 된다")
    void markFailed_marksJobFailed() {
        RebuildJob created = store.createIfAbsentActive("brand=APPLE", 10L)
                .orElseThrow();

        store.markFailed(created.getJobId(), "failed", "RuntimeException: boom");

        RebuildJob actual = store.find(created.getJobId()).orElseThrow();
        assertThat(actual.getStatus().name()).isEqualTo("FAILED");
        assertThat(actual.getMessage()).isEqualTo("failed");
        assertThat(actual.getFailureReason()).isEqualTo("RuntimeException: boom");
        assertThat(actual.getFinishedAt()).isNotNull();
        assertThat(actual.isActive()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 jobId 로 상태 변경을 호출해도 예외가 발생하지 않는다")
    void stateChangeMethods_doNothing_whenJobDoesNotExist() {
        UUID unknownJobId = UUID.randomUUID();

        store.markRunning(unknownJobId, "running");
        store.updateProgress(unknownJobId, 1L, "progress");
        store.markSucceeded(unknownJobId, "done");
        store.markFailed(unknownJobId, "failed", "reason");

        assertThat(store.find(unknownJobId)).isEmpty();
    }
}