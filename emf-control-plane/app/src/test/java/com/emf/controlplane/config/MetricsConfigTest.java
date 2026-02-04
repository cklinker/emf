package com.emf.controlplane.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MetricsConfig.
 * Verifies that business metrics are properly configured and can be recorded.
 * 
 * Requirements tested:
 * - 13.3: Expose Prometheus metrics via /actuator/metrics endpoint
 */
@ExtendWith(MockitoExtension.class)
class MetricsConfigTest {

    @Mock
    private ControlPlaneProperties properties;

    private MeterRegistry meterRegistry;
    private MetricsConfig.BusinessMetrics businessMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        businessMetrics = new MetricsConfig.BusinessMetrics(meterRegistry);
    }

    @Test
    @DisplayName("Should record collection created metric")
    void shouldRecordCollectionCreatedMetric() {
        // When
        businessMetrics.recordCollectionCreated();
        businessMetrics.recordCollectionCreated();

        // Then
        Counter counter = meterRegistry.find("emf.collections.created").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should record collection updated metric")
    void shouldRecordCollectionUpdatedMetric() {
        // When
        businessMetrics.recordCollectionUpdated();

        // Then
        Counter counter = meterRegistry.find("emf.collections.updated").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record collection deleted metric")
    void shouldRecordCollectionDeletedMetric() {
        // When
        businessMetrics.recordCollectionDeleted();

        // Then
        Counter counter = meterRegistry.find("emf.collections.deleted").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record field added metric")
    void shouldRecordFieldAddedMetric() {
        // When
        businessMetrics.recordFieldAdded();

        // Then
        Counter counter = meterRegistry.find("emf.fields.added").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record field updated metric")
    void shouldRecordFieldUpdatedMetric() {
        // When
        businessMetrics.recordFieldUpdated();

        // Then
        Counter counter = meterRegistry.find("emf.fields.updated").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record field deleted metric")
    void shouldRecordFieldDeletedMetric() {
        // When
        businessMetrics.recordFieldDeleted();

        // Then
        Counter counter = meterRegistry.find("emf.fields.deleted").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record role created metric")
    void shouldRecordRoleCreatedMetric() {
        // When
        businessMetrics.recordRoleCreated();

        // Then
        Counter counter = meterRegistry.find("emf.roles.created").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record policy created metric")
    void shouldRecordPolicyCreatedMetric() {
        // When
        businessMetrics.recordPolicyCreated();

        // Then
        Counter counter = meterRegistry.find("emf.policies.created").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record event published metric")
    void shouldRecordEventPublishedMetric() {
        // When
        businessMetrics.recordEventPublished();

        // Then
        Counter counter = meterRegistry.find("emf.events.published").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record event publish failed metric")
    void shouldRecordEventPublishFailedMetric() {
        // When
        businessMetrics.recordEventPublishFailed();

        // Then
        Counter counter = meterRegistry.find("emf.events.publish.failed").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record cache hit metric")
    void shouldRecordCacheHitMetric() {
        // When
        businessMetrics.recordCacheHit();

        // Then
        Counter counter = meterRegistry.find("emf.cache.hits").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record cache miss metric")
    void shouldRecordCacheMissMetric() {
        // When
        businessMetrics.recordCacheMiss();

        // Then
        Counter counter = meterRegistry.find("emf.cache.misses").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record JWKS fetched metric")
    void shouldRecordJwksFetchedMetric() {
        // When
        businessMetrics.recordJwksFetched();

        // Then
        Counter counter = meterRegistry.find("emf.jwks.fetched").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record JWKS fetch failed metric")
    void shouldRecordJwksFetchFailedMetric() {
        // When
        businessMetrics.recordJwksFetchFailed();

        // Then
        Counter counter = meterRegistry.find("emf.jwks.fetch.failed").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should provide collection operation timer")
    void shouldProvideCollectionOperationTimer() {
        // When
        Timer timer = businessMetrics.getCollectionOperationTimer();

        // Then
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getName()).isEqualTo("emf.collections.operation.duration");
    }

    @Test
    @DisplayName("Should provide event publish timer")
    void shouldProvideEventPublishTimer() {
        // When
        Timer timer = businessMetrics.getEventPublishTimer();

        // Then
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getName()).isEqualTo("emf.events.publish.duration");
    }

    @Test
    @DisplayName("Should provide JWKS fetch timer")
    void shouldProvideJwksFetchTimer() {
        // When
        Timer timer = businessMetrics.getJwksFetchTimer();

        // Then
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getName()).isEqualTo("emf.jwks.fetch.duration");
    }

    @Test
    @DisplayName("Should create custom counter with tags")
    void shouldCreateCustomCounterWithTags() {
        // When
        Counter counter = businessMetrics.createCounter(
                "emf.custom.counter",
                "A custom counter",
                "operation", "test",
                "status", "success"
        );
        counter.increment();

        // Then
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        assertThat(counter.getId().getTag("operation")).isEqualTo("test");
        assertThat(counter.getId().getTag("status")).isEqualTo("success");
    }

    @Test
    @DisplayName("Should create custom timer with tags")
    void shouldCreateCustomTimerWithTags() {
        // When
        Timer timer = businessMetrics.createTimer(
                "emf.custom.timer",
                "A custom timer",
                "operation", "test"
        );

        // Then
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getName()).isEqualTo("emf.custom.timer");
        assertThat(timer.getId().getTag("operation")).isEqualTo("test");
    }

    @Test
    @DisplayName("Should accumulate multiple metric recordings")
    void shouldAccumulateMultipleMetricRecordings() {
        // When
        for (int i = 0; i < 10; i++) {
            businessMetrics.recordCollectionCreated();
        }

        // Then
        Counter counter = meterRegistry.find("emf.collections.created").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(10.0);
    }
}
