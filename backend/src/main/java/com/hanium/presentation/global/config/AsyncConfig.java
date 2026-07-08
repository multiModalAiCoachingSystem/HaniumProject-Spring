// 역할: 백엔드의 AsyncConfig 기능을 담당하는 Java 코드입니다.
package com.hanium.presentation.global.config;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {
    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    // 함수 역할: analysisTaskExecutor에서 발표 분석 요청, 엔진 호출, 결과 처리 흐름을 조율합니다.
    @Bean(name = "analysisTaskExecutor")
    public ThreadPoolTaskExecutor analysisTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("analysis-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    // 함수 역할: getAsyncExecutor에서 필요한 값이나 상태를 조회해 반환합니다.
    @Override
    public Executor getAsyncExecutor() {
        return analysisTaskExecutor();
    }

    // 함수 역할: getAsyncUncaughtExceptionHandler에서 필요한 값이나 상태를 조회해 반환합니다.
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable exception, Method method, Object... params) ->
                log.error("Async task failed. method={}, params={}", method.getName(), params, exception);
    }
}
