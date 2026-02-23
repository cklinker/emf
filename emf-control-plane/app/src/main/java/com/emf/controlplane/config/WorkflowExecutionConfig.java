package com.emf.controlplane.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

/**
 * Configuration for workflow execution infrastructure.
 * <p>
 * Provides:
 * <ul>
 *   <li>Dedicated thread pool for workflow execution (prevents starving HTTP threads)</li>
 *   <li>Connection-pooled RestTemplate for outbound HTTP action handlers</li>
 * </ul>
 */
@Configuration
public class WorkflowExecutionConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionConfig.class);

    // --- Thread Pool ---

    @Value("${emf.workflow.thread-pool-size:10}")
    private int threadPoolSize;

    @Value("${emf.workflow.thread-pool-max-size:20}")
    private int threadPoolMaxSize;

    @Value("${emf.workflow.thread-pool-queue-capacity:100}")
    private int threadPoolQueueCapacity;

    // --- HTTP Connection Pool ---

    @Value("${emf.workflow.http.connect-timeout-ms:5000}")
    private int httpConnectTimeout;

    @Value("${emf.workflow.http.read-timeout-ms:30000}")
    private int httpReadTimeout;

    /**
     * Dedicated thread pool for workflow action execution.
     * <p>
     * Separates workflow processing from HTTP request threads and Kafka consumer threads
     * to prevent workflow execution from starving other services.
     * <p>
     * Uses caller-runs rejection policy — if the queue is full, the calling thread
     * executes the task directly, providing natural back-pressure.
     */
    @Bean(name = "workflowExecutor")
    public Executor workflowExecutor() {
        log.info("Configuring workflow executor: coreSize={}, maxSize={}, queueCapacity={}",
            threadPoolSize, threadPoolMaxSize, threadPoolQueueCapacity);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadPoolSize);
        executor.setMaxPoolSize(threadPoolMaxSize);
        executor.setQueueCapacity(threadPoolQueueCapacity);
        executor.setThreadNamePrefix("workflow-exec-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Connection-pooled RestTemplate for HTTP-based action handlers
     * (HttpCalloutActionHandler, OutboundMessageActionHandler).
     * <p>
     * Configurable connect and read timeouts prevent slow external APIs
     * from blocking workflow execution indefinitely.
     */
    @Bean(name = "workflowRestTemplate")
    public RestTemplate workflowRestTemplate() {
        log.info("Configuring workflow RestTemplate: connectTimeout={}ms, readTimeout={}ms",
            httpConnectTimeout, httpReadTimeout);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(httpConnectTimeout);
        factory.setReadTimeout(httpReadTimeout);

        return new RestTemplate(factory);
    }
}
