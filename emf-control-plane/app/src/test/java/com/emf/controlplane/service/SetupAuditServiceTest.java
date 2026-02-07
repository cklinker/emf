package com.emf.controlplane.service;

import com.emf.controlplane.entity.SetupAuditTrail;
import com.emf.controlplane.repository.SetupAuditTrailRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SetupAuditService")
class SetupAuditServiceTest {

    @Mock private SetupAuditTrailRepository auditRepository;

    private SetupAuditService service;

    @BeforeEach
    void setUp() {
        service = new SetupAuditService(auditRepository, new ObjectMapper());
        TenantContextHolder.set("tenant-1", "test-tenant");
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user-1", null));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should log audit trail entry")
    void shouldLogAuditTrail() {
        when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.log("CREATED", "Collections", "Collection", "col-1", "Accounts", null, "{\"name\":\"Accounts\"}");

        ArgumentCaptor<SetupAuditTrail> captor = ArgumentCaptor.forClass(SetupAuditTrail.class);
        verify(auditRepository).save(captor.capture());

        SetupAuditTrail saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo("tenant-1");
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getAction()).isEqualTo("CREATED");
        assertThat(saved.getSection()).isEqualTo("Collections");
        assertThat(saved.getEntityType()).isEqualTo("Collection");
        assertThat(saved.getEntityId()).isEqualTo("col-1");
        assertThat(saved.getEntityName()).isEqualTo("Accounts");
        assertThat(saved.getNewValue()).isEqualTo("{\"name\":\"Accounts\"}");
    }

    @Test
    @DisplayName("should skip audit when no tenant context")
    void shouldSkipWhenNoTenant() {
        TenantContextHolder.clear();

        service.log("CREATED", "Collections", "Collection", "col-1", "Accounts", null, null);

        verify(auditRepository, never()).save(any());
    }

    @Test
    @DisplayName("should skip audit when no user context")
    void shouldSkipWhenNoUser() {
        SecurityContextHolder.clearContext();

        service.log("CREATED", "Collections", "Collection", "col-1", "Accounts", null, null);

        verify(auditRepository, never()).save(any());
    }

    @Test
    @DisplayName("should return filtered audit trail")
    void shouldReturnFilteredAuditTrail() {
        PageRequest pageable = PageRequest.of(0, 50);
        when(auditRepository.findFiltered("tenant-1", null, null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        Page<?> result = service.getAuditTrail(null, null, null, null, null, pageable);

        assertThat(result).isEmpty();
        verify(auditRepository).findFiltered("tenant-1", null, null, null, null, null, pageable);
    }
}
