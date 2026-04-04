package io.kelta.worker.service;

import com.svix.Svix;
import com.svix.api.Application;
import com.svix.models.ApplicationIn;
import com.svix.models.ApplicationOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SvixTenantService")
class SvixTenantServiceTest {

    @Mock
    private Svix svix;

    @Mock
    private Application applicationEndpoint;

    private SvixTenantService service;

    @BeforeEach
    void setUp() {
        when(svix.getApplication()).thenReturn(applicationEndpoint);
        service = new SvixTenantService(svix);
    }

    @Nested
    @DisplayName("ensureApplication")
    class EnsureApplication {

        @Test
        @DisplayName("creates application with tenant name")
        void createsWithTenantName() throws Exception {
            when(applicationEndpoint.getOrCreate(any(ApplicationIn.class))).thenReturn(new ApplicationOut());

            service.ensureApplication("tenant-1", "Acme Corp");

            verify(applicationEndpoint).getOrCreate(any(ApplicationIn.class));
        }

        @Test
        @DisplayName("handles Svix API errors gracefully")
        void handlesErrors() throws Exception {
            when(applicationEndpoint.getOrCreate(any(ApplicationIn.class)))
                    .thenThrow(new RuntimeException("API error"));

            service.ensureApplication("tenant-1", "Test");

            // Should not throw
        }
    }

    @Nested
    @DisplayName("deleteApplication")
    class DeleteApplication {

        @Test
        @DisplayName("deletes application by tenant ID")
        void deletesApplication() throws Exception {
            service.deleteApplication("tenant-1");

            verify(applicationEndpoint).delete("tenant-1");
        }

        @Test
        @DisplayName("handles deletion errors gracefully")
        void handlesErrors() throws Exception {
            doThrow(new RuntimeException("Not found")).when(applicationEndpoint).delete("tenant-1");

            service.deleteApplication("tenant-1");

            // Should not throw
        }
    }
}
