package com.product.config;

import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AsyncConfigTest {

    private final AsyncConfig asyncConfig = new AsyncConfig();

    @Test
    void taskExecutor_returnsConfiguredThreadPoolTaskExecutor() {
        Executor executor = asyncConfig.taskExecutor();

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("product-cache-async-");
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(4);
    }

    @Test
    void getAsyncUncaughtExceptionHandler_handlesExceptionWithoutRethrow() throws Exception {
        AsyncUncaughtExceptionHandler handler = asyncConfig.getAsyncUncaughtExceptionHandler();
        Method method = AsyncConfigTest.class.getDeclaredMethod("asyncTarget");

        assertThatCode(() -> handler.handleUncaughtException(new RuntimeException("async failed"), method, 1L))
                .doesNotThrowAnyException();
    }

    @SuppressWarnings("unused")
    private void asyncTarget() {
    }
}
