package com.society.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables Spring's {@code @Async} support and provides a dedicated executor for
 * long-running background work (e.g. bulk owner emailing) so such work runs off
 * the request thread and the HTTP response can return immediately.
 *
 * <p>The owner-email bulk send previously ran the whole per-recipient SMTP loop
 * synchronously inside the request. For large recipient sets this could exceed the
 * client's request timeout: the browser reported a failure while the server kept
 * sending, so emails went out but the UI said "failed". Moving the send loop onto
 * this executor decouples delivery from the request lifetime.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Executor backing {@code @Async("ownerEmailExecutor")} bulk-email sends.
     *
     * <p>Kept deliberately small: outbound SMTP is the bottleneck and we do not want
     * many concurrent sessions against the mail provider. Tasks that exceed the pool
     * and queue capacity run on the caller thread (CallerRuns) rather than being
     * rejected, so a send is never silently dropped.</p>
     */
    @Bean(name = "ownerEmailExecutor")
    public Executor ownerEmailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("owner-email-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
