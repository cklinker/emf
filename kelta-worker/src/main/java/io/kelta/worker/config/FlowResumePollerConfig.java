package io.kelta.worker.config;

import io.kelta.runtime.flow.FlowEngine;
import io.kelta.runtime.flow.FlowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.UUID;

/**
 * Schedules the wait-state resume poller.
 *
 * <p>Long ({@code seconds} &gt; 10) and timestamp-based Wait states park their
 * execution WAITING and record a {@code flow_pending_resume} row with a wake-up
 * time. This poller claims due rows on a configurable interval (default 10s)
 * via {@code SELECT FOR UPDATE SKIP LOCKED} — so exactly one pod resumes each
 * execution — and hands them to {@link FlowEngine#resumeExecution(String)}.
 *
 * <p>Event-based waits ({@code resume_event} set, {@code resume_at} null) are
 * never time-claimed; they resume via {@code claimPendingResumeByEvent} when
 * the event arrives.
 *
 * <p>Enabled by default. Disable with {@code kelta.flow.resume.enabled=false}.
 *
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "kelta.flow.resume.enabled", havingValue = "true", matchIfMissing = true)
public class FlowResumePollerConfig {

    private static final Logger log = LoggerFactory.getLogger(FlowResumePollerConfig.class);

    private static final int CLAIM_BATCH_SIZE = 25;

    private final FlowStore flowStore;
    private final FlowEngine flowEngine;
    private final String instanceId = "worker-" + UUID.randomUUID();

    public FlowResumePollerConfig(FlowStore flowStore, FlowEngine flowEngine) {
        this.flowStore = flowStore;
        this.flowEngine = flowEngine;
        log.info("Flow resume poller enabled — claiming due flow_pending_resume rows as {}", instanceId);
    }

    @Scheduled(fixedDelayString = "${kelta.flow.resume.poll-interval-ms:10000}")
    public void pollAndResume() {
        try {
            List<String> executionIds = flowStore.claimPendingResumes(instanceId, CLAIM_BATCH_SIZE);
            for (String executionId : executionIds) {
                log.info("Resuming waiting flow execution {}", executionId);
                flowEngine.resumeExecution(executionId);
            }
        } catch (Exception e) {
            log.error("Flow resume poll cycle failed: {}", e.getMessage(), e);
        }
    }
}
