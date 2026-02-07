package com.emf.controlplane.service;

import com.emf.controlplane.dto.GovernorLimits;
import com.emf.controlplane.exception.GovernorLimitExceededException;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.controlplane.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GovernorLimitsService")
class GovernorLimitsServiceTest {

    @Mock private TenantService tenantService;
    @Mock private UserRepository userRepository;
    @Mock private CollectionRepository collectionRepository;
    @Mock private FieldRepository fieldRepository;

    private GovernorLimitsService service;

    private static final String TENANT_ID = "tenant-1";

    @BeforeEach
    void setUp() {
        service = new GovernorLimitsService(tenantService, userRepository,
                collectionRepository, fieldRepository, null);
    }

    @Nested
    @DisplayName("checkUserLimit")
    class CheckUserLimitTests {

        @Test
        @DisplayName("should pass when under limit")
        void passWhenUnderLimit() {
            when(tenantService.getGovernorLimits(TENANT_ID)).thenReturn(GovernorLimits.defaults());
            when(userRepository.countByTenantId(TENANT_ID)).thenReturn(50L);

            service.checkUserLimit(TENANT_ID);
            // no exception
        }

        @Test
        @DisplayName("should throw when at limit")
        void throwWhenAtLimit() {
            when(tenantService.getGovernorLimits(TENANT_ID)).thenReturn(GovernorLimits.defaults());
            when(userRepository.countByTenantId(TENANT_ID)).thenReturn(100L);

            assertThatThrownBy(() -> service.checkUserLimit(TENANT_ID))
                    .isInstanceOf(GovernorLimitExceededException.class)
                    .hasMessageContaining("User limit exceeded");
        }
    }

    @Nested
    @DisplayName("checkCollectionLimit")
    class CheckCollectionLimitTests {

        @Test
        @DisplayName("should pass when under limit")
        void passWhenUnderLimit() {
            when(tenantService.getGovernorLimits(TENANT_ID)).thenReturn(GovernorLimits.defaults());
            when(collectionRepository.countByTenantIdAndActiveTrue(TENANT_ID)).thenReturn(10L);

            service.checkCollectionLimit(TENANT_ID);
        }

        @Test
        @DisplayName("should throw when at limit")
        void throwWhenAtLimit() {
            when(tenantService.getGovernorLimits(TENANT_ID)).thenReturn(GovernorLimits.defaults());
            when(collectionRepository.countByTenantIdAndActiveTrue(TENANT_ID)).thenReturn(200L);

            assertThatThrownBy(() -> service.checkCollectionLimit(TENANT_ID))
                    .isInstanceOf(GovernorLimitExceededException.class)
                    .hasMessageContaining("Collection limit exceeded");
        }
    }

    @Nested
    @DisplayName("checkFieldLimit")
    class CheckFieldLimitTests {

        @Test
        @DisplayName("should pass when under limit")
        void passWhenUnderLimit() {
            when(tenantService.getGovernorLimits(TENANT_ID)).thenReturn(GovernorLimits.defaults());
            when(fieldRepository.countByCollectionIdAndActiveTrue("col-1")).thenReturn(100L);

            service.checkFieldLimit(TENANT_ID, "col-1");
        }

        @Test
        @DisplayName("should throw when at limit")
        void throwWhenAtLimit() {
            when(tenantService.getGovernorLimits(TENANT_ID)).thenReturn(GovernorLimits.defaults());
            when(fieldRepository.countByCollectionIdAndActiveTrue("col-1")).thenReturn(500L);

            assertThatThrownBy(() -> service.checkFieldLimit(TENANT_ID, "col-1"))
                    .isInstanceOf(GovernorLimitExceededException.class)
                    .hasMessageContaining("Field limit exceeded");
        }
    }

    @Nested
    @DisplayName("getStatus")
    class GetStatusTests {

        @Test
        @DisplayName("should return current usage status")
        void returnStatus() {
            when(tenantService.getGovernorLimits(TENANT_ID)).thenReturn(GovernorLimits.defaults());
            when(userRepository.countByTenantId(TENANT_ID)).thenReturn(25L);
            when(collectionRepository.countByTenantIdAndActiveTrue(TENANT_ID)).thenReturn(5L);

            GovernorLimitsService.GovernorLimitsStatus status = service.getStatus(TENANT_ID);

            assertThat(status.usersUsed()).isEqualTo(25);
            assertThat(status.usersLimit()).isEqualTo(100);
            assertThat(status.collectionsUsed()).isEqualTo(5);
            assertThat(status.collectionsLimit()).isEqualTo(200);
        }
    }
}
