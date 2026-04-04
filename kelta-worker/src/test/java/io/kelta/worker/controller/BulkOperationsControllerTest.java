package io.kelta.worker.controller;

import tools.jackson.databind.ObjectMapper;
import io.kelta.worker.repository.BulkJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkOperationsControllerTest {

    @Mock
    private BulkJobRepository bulkJobRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private BulkOperationsController controller;

    private static final String TENANT_ID = "tenant-1";
    private static final String USER_EMAIL = "user@example.com";
    private static final String COLLECTION_ID = "col-1";
    private static final String JOB_ID = "job-1";

    @BeforeEach
    void setUp() {
        controller = new BulkOperationsController(bulkJobRepository, new ObjectMapper(), jdbcTemplate);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getAttributes(Map<String, Object> body) {
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return (Map<String, Object>) data.get("attributes");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getData(Map<String, Object> body) {
        return (Map<String, Object>) body.get("data");
    }

    private Map<String, Object> validCreateBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collectionId", COLLECTION_ID);
        body.put("operation", "INSERT");
        body.put("records", List.of(
                Map.of("name", "Record 1"),
                Map.of("name", "Record 2")
        ));
        return body;
    }

    private void mockCollectionExists() {
        when(jdbcTemplate.queryForList(anyString(), eq(COLLECTION_ID), eq(TENANT_ID)))
                .thenReturn(List.of(Map.of("id", COLLECTION_ID)));
    }

    // ==================== CREATE Tests ====================

    @Nested
    @DisplayName("POST /api/bulk-jobs")
    class CreateJobTests {

        @Test
        @DisplayName("Should create a job successfully")
        void createJob_success() {
            Map<String, Object> body = validCreateBody();
            mockCollectionExists();
            when(bulkJobRepository.create(anyString(), anyString(), anyString(),
                    anyString(), anyInt(), any(), anyString(), anyString(), any(), anyInt()))
                    .thenReturn(JOB_ID);

            ResponseEntity<Map<String, Object>> response = controller.createJob(TENANT_ID, USER_EMAIL, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            Map<String, Object> attributes = getAttributes(response.getBody());
            assertThat(attributes.get("status")).isEqualTo("QUEUED");
            assertThat(attributes.get("operation")).isEqualTo("INSERT");
            assertThat(attributes.get("totalRecords")).isEqualTo(2);
            assertThat(attributes.get("batchSize")).isEqualTo(200);
        }

        @Test
        @DisplayName("Should reject missing collectionId")
        void createJob_missingCollectionId() {
            Map<String, Object> body = validCreateBody();
            body.remove("collectionId");

            ResponseEntity<Map<String, Object>> response = controller.createJob(TENANT_ID, USER_EMAIL, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should reject invalid operation")
        void createJob_invalidOperation() {
            Map<String, Object> body = validCreateBody();
            body.put("operation", "INVALID");

            ResponseEntity<Map<String, Object>> response = controller.createJob(TENANT_ID, USER_EMAIL, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should reject empty records")
        void createJob_emptyRecords() {
            Map<String, Object> body = validCreateBody();
            body.put("records", List.of());

            ResponseEntity<Map<String, Object>> response = controller.createJob(TENANT_ID, USER_EMAIL, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should reject missing records")
        void createJob_missingRecords() {
            Map<String, Object> body = validCreateBody();
            body.remove("records");

            ResponseEntity<Map<String, Object>> response = controller.createJob(TENANT_ID, USER_EMAIL, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should return 404 when collection not found")
        void createJob_collectionNotFound() {
            Map<String, Object> body = validCreateBody();
            when(jdbcTemplate.queryForList(anyString(), eq(COLLECTION_ID), eq(TENANT_ID)))
                    .thenReturn(List.of());

            ResponseEntity<Map<String, Object>> response = controller.createJob(TENANT_ID, USER_EMAIL, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Should accept custom batch size")
        void createJob_customBatchSize() {
            Map<String, Object> body = validCreateBody();
            body.put("batchSize", 50);
            mockCollectionExists();
            when(bulkJobRepository.create(anyString(), anyString(), anyString(),
                    anyString(), eq(50), any(), anyString(), anyString(), any(), anyInt()))
                    .thenReturn(JOB_ID);

            ResponseEntity<Map<String, Object>> response = controller.createJob(TENANT_ID, USER_EMAIL, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            Map<String, Object> attributes = getAttributes(response.getBody());
            assertThat(attributes.get("batchSize")).isEqualTo(50);
        }

        @Test
        @DisplayName("Should cap batch size at maximum")
        void createJob_capBatchSize() {
            Map<String, Object> body = validCreateBody();
            body.put("batchSize", 5000);
            mockCollectionExists();
            when(bulkJobRepository.create(anyString(), anyString(), anyString(),
                    anyString(), eq(1000), any(), anyString(), anyString(), any(), anyInt()))
                    .thenReturn(JOB_ID);

            ResponseEntity<Map<String, Object>> response = controller.createJob(TENANT_ID, USER_EMAIL, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            Map<String, Object> attributes = getAttributes(response.getBody());
            assertThat(attributes.get("batchSize")).isEqualTo(1000);
        }

        @Test
        @DisplayName("Should accept all valid operations")
        void createJob_allValidOperations() {
            for (String op : List.of("INSERT", "UPDATE", "UPSERT", "DELETE")) {
                Map<String, Object> body = validCreateBody();
                body.put("operation", op);
                mockCollectionExists();
                when(bulkJobRepository.create(anyString(), anyString(), eq(op),
                        anyString(), anyInt(), any(), anyString(), anyString(), any(), anyInt()))
                        .thenReturn(JOB_ID);

                ResponseEntity<Map<String, Object>> response = controller.createJob(TENANT_ID, USER_EMAIL, body);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            }
        }

        @Test
        @DisplayName("Should accept lowercase operation")
        void createJob_lowercaseOperation() {
            Map<String, Object> body = validCreateBody();
            body.put("operation", "insert");
            mockCollectionExists();
            when(bulkJobRepository.create(anyString(), anyString(), eq("INSERT"),
                    anyString(), anyInt(), any(), anyString(), anyString(), any(), anyInt()))
                    .thenReturn(JOB_ID);

            ResponseEntity<Map<String, Object>> response = controller.createJob(TENANT_ID, USER_EMAIL, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    // ==================== GET Tests ====================

    @Nested
    @DisplayName("GET /api/bulk-jobs/{id}")
    class GetJobTests {

        @Test
        @DisplayName("Should return job details")
        void getJob_success() {
            Map<String, Object> job = new LinkedHashMap<>();
            job.put("id", JOB_ID);
            job.put("status", "PROCESSING");
            job.put("operation", "INSERT");
            job.put("collection_id", COLLECTION_ID);
            job.put("total_records", 100);
            job.put("processed_records", 50);
            job.put("success_records", 48);
            job.put("error_records", 2);
            job.put("batch_size", 200);
            job.put("external_id_field", null);
            job.put("content_type", "application/json");
            job.put("error_message", null);
            job.put("created_by", USER_EMAIL);
            job.put("started_at", null);
            job.put("completed_at", null);
            job.put("created_at", null);
            job.put("updated_at", null);

            when(bulkJobRepository.findByIdAndTenant(JOB_ID, TENANT_ID))
                    .thenReturn(Optional.of(job));

            ResponseEntity<Map<String, Object>> response = controller.getJob(JOB_ID, TENANT_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> attributes = getAttributes(response.getBody());
            assertThat(attributes.get("status")).isEqualTo("PROCESSING");
            assertThat(attributes.get("processedRecords")).isEqualTo(50);
            assertThat(attributes.get("successRecords")).isEqualTo(48);
            assertThat(attributes.get("errorRecords")).isEqualTo(2);
        }

        @Test
        @DisplayName("Should return 404 when job not found")
        void getJob_notFound() {
            when(bulkJobRepository.findByIdAndTenant(JOB_ID, TENANT_ID))
                    .thenReturn(Optional.empty());

            ResponseEntity<Map<String, Object>> response = controller.getJob(JOB_ID, TENANT_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ==================== LIST Tests ====================

    @Nested
    @DisplayName("GET /api/bulk-jobs")
    class ListJobsTests {

        @Test
        @DisplayName("Should return list of jobs")
        void listJobs_success() {
            Map<String, Object> job1 = new LinkedHashMap<>();
            job1.put("id", "job-1");
            job1.put("status", "COMPLETED");
            job1.put("operation", "INSERT");
            job1.put("collection_id", COLLECTION_ID);
            job1.put("total_records", 10);
            job1.put("processed_records", 10);
            job1.put("success_records", 10);
            job1.put("error_records", 0);
            job1.put("batch_size", 200);
            job1.put("external_id_field", null);
            job1.put("content_type", "application/json");
            job1.put("error_message", null);
            job1.put("created_by", USER_EMAIL);
            job1.put("started_at", null);
            job1.put("completed_at", null);
            job1.put("created_at", null);
            job1.put("updated_at", null);

            when(bulkJobRepository.findByTenant(TENANT_ID, 20, 0))
                    .thenReturn(List.of(job1));

            ResponseEntity<Map<String, Object>> response = controller.listJobs(TENANT_ID, 20, 0);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            List<Object> data = (List<Object>) response.getBody().get("data");
            assertThat(data).hasSize(1);
        }
    }

    // ==================== RESULTS Tests ====================

    @Nested
    @DisplayName("GET /api/bulk-jobs/{id}/results")
    class GetResultsTests {

        @Test
        @DisplayName("Should return results with pagination")
        void getResults_success() {
            when(bulkJobRepository.findByIdAndTenant(JOB_ID, TENANT_ID))
                    .thenReturn(Optional.of(Map.of("id", JOB_ID)));

            Map<String, Object> result1 = new LinkedHashMap<>();
            result1.put("id", "result-1");
            result1.put("record_index", 0);
            result1.put("record_id", "rec-1");
            result1.put("status", "SUCCESS");
            result1.put("error_message", null);

            when(bulkJobRepository.findResults(JOB_ID, TENANT_ID, 50, 0, null))
                    .thenReturn(List.of(result1));
            when(bulkJobRepository.countResults(JOB_ID, TENANT_ID, null))
                    .thenReturn(1);

            ResponseEntity<Map<String, Object>> response = controller.getResults(JOB_ID, TENANT_ID, 50, 0, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
            assertThat(meta.get("totalCount")).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return 404 when job not found")
        void getResults_jobNotFound() {
            when(bulkJobRepository.findByIdAndTenant(JOB_ID, TENANT_ID))
                    .thenReturn(Optional.empty());

            ResponseEntity<Map<String, Object>> response = controller.getResults(JOB_ID, TENANT_ID, 50, 0, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Should filter results by status")
        void getResults_filterByStatus() {
            when(bulkJobRepository.findByIdAndTenant(JOB_ID, TENANT_ID))
                    .thenReturn(Optional.of(Map.of("id", JOB_ID)));
            when(bulkJobRepository.findResults(JOB_ID, TENANT_ID, 50, 0, "FAILURE"))
                    .thenReturn(List.of());
            when(bulkJobRepository.countResults(JOB_ID, TENANT_ID, "FAILURE"))
                    .thenReturn(0);

            ResponseEntity<Map<String, Object>> response = controller.getResults(
                    JOB_ID, TENANT_ID, 50, 0, "FAILURE");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ==================== ABORT Tests ====================

    @Nested
    @DisplayName("POST /api/bulk-jobs/{id}/abort")
    class AbortJobTests {

        @Test
        @DisplayName("Should abort a queued job")
        void abortJob_success() {
            when(bulkJobRepository.abort(JOB_ID, TENANT_ID)).thenReturn(1);

            Map<String, Object> abortedJob = new LinkedHashMap<>();
            abortedJob.put("id", JOB_ID);
            abortedJob.put("status", "ABORTED");
            abortedJob.put("operation", "INSERT");
            abortedJob.put("collection_id", COLLECTION_ID);
            abortedJob.put("total_records", 10);
            abortedJob.put("processed_records", 0);
            abortedJob.put("success_records", 0);
            abortedJob.put("error_records", 0);
            abortedJob.put("batch_size", 200);
            abortedJob.put("external_id_field", null);
            abortedJob.put("content_type", "application/json");
            abortedJob.put("error_message", null);
            abortedJob.put("created_by", USER_EMAIL);
            abortedJob.put("started_at", null);
            abortedJob.put("completed_at", null);
            abortedJob.put("created_at", null);
            abortedJob.put("updated_at", null);

            when(bulkJobRepository.findByIdAndTenant(JOB_ID, TENANT_ID))
                    .thenReturn(Optional.of(abortedJob));

            ResponseEntity<Map<String, Object>> response = controller.abortJob(JOB_ID, TENANT_ID, USER_EMAIL);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> attributes = getAttributes(response.getBody());
            assertThat(attributes.get("status")).isEqualTo("ABORTED");
        }

        @Test
        @DisplayName("Should return 404 when job not found")
        void abortJob_notFound() {
            when(bulkJobRepository.abort(JOB_ID, TENANT_ID)).thenReturn(0);
            when(bulkJobRepository.findByIdAndTenant(JOB_ID, TENANT_ID))
                    .thenReturn(Optional.empty());

            ResponseEntity<Map<String, Object>> response = controller.abortJob(JOB_ID, TENANT_ID, USER_EMAIL);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Should return 422 when job already completed")
        void abortJob_alreadyCompleted() {
            when(bulkJobRepository.abort(JOB_ID, TENANT_ID)).thenReturn(0);
            when(bulkJobRepository.findByIdAndTenant(JOB_ID, TENANT_ID))
                    .thenReturn(Optional.of(Map.of("id", JOB_ID, "status", "COMPLETED")));

            ResponseEntity<Map<String, Object>> response = controller.abortJob(JOB_ID, TENANT_ID, USER_EMAIL);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
}
