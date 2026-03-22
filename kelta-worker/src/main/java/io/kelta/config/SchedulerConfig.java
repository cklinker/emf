package io.kelta.worker.config;

import io.kelta.worker.service.ScheduledJobExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Configuration for the scheduled job execution subsystem.
 *
 * <p>Polls the scheduled_jobs table on a configurable interval (default 60s)
 * and executes due jobs via {@link ScheduledJobExecutorService}.
 *
 * <p>Enabled by default. Disable with {@code kelta.scheduler.enabled=false}.
 *
 * @since 1.0.0
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "kelta.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulerConfig.class);

    private final ScheduledJobExecutorService executorService;

    public SchedulerConfig(ScheduledJobExecutorService executorService) {
        this.executorService = executorService;
        log.info("Scheduled job executor enabled — polling for due jobs");
    }

    @Bean
    public ThreadPoolTaskScheduler schedulerTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setErrorHandler(t -> log.error("Scheduler error: {}", t.getMessage(), t));
        return scheduler;
    }

    @Scheduled(fixedDelayString = "${kelta.scheduler.poll-interval-ms:60000}")
    public void pollAndExecute() {
        try {
            executorService.executeAll();
        } catch (Exception e) {
            log.error("Scheduler poll cycle failed: {}", e.getMessage(), e);
        }
    }
}
