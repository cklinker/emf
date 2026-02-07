package com.emf.controlplane.service;

import com.emf.controlplane.dto.*;
import com.emf.controlplane.entity.OrgWideDefault;
import com.emf.controlplane.entity.RecordShare;
import com.emf.controlplane.entity.SharingRule;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.OrgWideDefaultRepository;
import com.emf.controlplane.repository.RecordShareRepository;
import com.emf.controlplane.repository.SharingRuleRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SharingService")
class SharingServiceTest {

    @Mock private OrgWideDefaultRepository owdRepository;
    @Mock private SharingRuleRepository sharingRuleRepository;
    @Mock private RecordShareRepository recordShareRepository;
    @Mock private CollectionRepository collectionRepository;

    private SharingService service;

    private static final String TENANT_ID = "tenant-1";
    private static final String COLLECTION_ID = "col-1";

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(TENANT_ID, "test-tenant");
        service = new SharingService(owdRepository, sharingRuleRepository,
                recordShareRepository, collectionRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("OWD operations")
    class OwdTests {

        @Test
        @DisplayName("should return default OWD when none configured")
        void returnDefaultOwdWhenNoneConfigured() {
            when(owdRepository.findByTenantIdAndCollectionId(TENANT_ID, COLLECTION_ID))
                    .thenReturn(Optional.empty());
            when(collectionRepository.existsById(COLLECTION_ID)).thenReturn(true);

            OrgWideDefaultDto result = service.getOwd(COLLECTION_ID);

            assertThat(result.getInternalAccess()).isEqualTo("PUBLIC_READ_WRITE");
            assertThat(result.getExternalAccess()).isEqualTo("PRIVATE");
        }

        @Test
        @DisplayName("should create OWD when setting for first time")
        void createOwdWhenSettingFirstTime() {
            when(collectionRepository.existsById(COLLECTION_ID)).thenReturn(true);
            when(owdRepository.findByTenantIdAndCollectionId(TENANT_ID, COLLECTION_ID))
                    .thenReturn(Optional.empty());
            OrgWideDefault saved = new OrgWideDefault(TENANT_ID, COLLECTION_ID, "PRIVATE");
            when(owdRepository.save(any(OrgWideDefault.class))).thenReturn(saved);

            SetOwdRequest request = new SetOwdRequest("PRIVATE");
            OrgWideDefaultDto result = service.setOwd(COLLECTION_ID, request);

            assertThat(result.getInternalAccess()).isEqualTo("PRIVATE");
            verify(owdRepository).save(any(OrgWideDefault.class));
        }

        @Test
        @DisplayName("should throw when collection does not exist")
        void throwWhenCollectionNotFound() {
            when(collectionRepository.existsById(COLLECTION_ID)).thenReturn(false);

            assertThatThrownBy(() -> service.getOwd(COLLECTION_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Sharing Rule operations")
    class SharingRuleTests {

        @Test
        @DisplayName("should create a sharing rule")
        void createSharingRule() {
            when(collectionRepository.existsById(COLLECTION_ID)).thenReturn(true);
            SharingRule saved = new SharingRule();
            saved.setName("Test Rule");
            saved.setCollectionId(COLLECTION_ID);
            saved.setRuleType("OWNER_BASED");
            saved.setSharedTo("role-1");
            saved.setSharedToType("ROLE");
            saved.setAccessLevel("READ");
            when(sharingRuleRepository.save(any(SharingRule.class))).thenReturn(saved);

            CreateSharingRuleRequest request = new CreateSharingRuleRequest();
            request.setName("Test Rule");
            request.setRuleType("OWNER_BASED");
            request.setSharedTo("role-1");
            request.setSharedToType("ROLE");
            request.setAccessLevel("READ");

            SharingRuleDto result = service.createRule(COLLECTION_ID, request);

            assertThat(result.getName()).isEqualTo("Test Rule");
            assertThat(result.getAccessLevel()).isEqualTo("READ");
        }

        @Test
        @DisplayName("should delete a sharing rule")
        void deleteSharingRule() {
            when(sharingRuleRepository.existsById("rule-1")).thenReturn(true);

            service.deleteRule("rule-1");

            verify(sharingRuleRepository).deleteById("rule-1");
        }

        @Test
        @DisplayName("should throw when deleting non-existent rule")
        void throwWhenDeletingNonExistentRule() {
            when(sharingRuleRepository.existsById("rule-1")).thenReturn(false);

            assertThatThrownBy(() -> service.deleteRule("rule-1"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Record Share operations")
    class RecordShareTests {

        @Test
        @DisplayName("should create a record share")
        void createRecordShare() {
            RecordShare saved = new RecordShare();
            saved.setCollectionId(COLLECTION_ID);
            saved.setRecordId("rec-1");
            saved.setSharedWithId("user-2");
            saved.setSharedWithType("USER");
            saved.setAccessLevel("READ");
            saved.setCreatedBy("user-1");
            when(recordShareRepository.save(any(RecordShare.class))).thenReturn(saved);

            CreateRecordShareRequest request = new CreateRecordShareRequest();
            request.setRecordId("rec-1");
            request.setSharedWithId("user-2");
            request.setSharedWithType("USER");
            request.setAccessLevel("READ");

            RecordShareDto result = service.createRecordShare(COLLECTION_ID, request, "user-1");

            assertThat(result.getSharedWithId()).isEqualTo("user-2");
            assertThat(result.getAccessLevel()).isEqualTo("READ");
        }

        @Test
        @DisplayName("should list shares for a record")
        void listRecordShares() {
            RecordShare share = new RecordShare();
            share.setCollectionId(COLLECTION_ID);
            share.setRecordId("rec-1");
            share.setSharedWithId("user-2");
            share.setSharedWithType("USER");
            share.setAccessLevel("READ");
            share.setCreatedBy("user-1");
            when(recordShareRepository.findByCollectionIdAndRecordId(COLLECTION_ID, "rec-1"))
                    .thenReturn(List.of(share));

            List<RecordShareDto> result = service.listRecordShares(COLLECTION_ID, "rec-1");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSharedWithId()).isEqualTo("user-2");
        }
    }
}
