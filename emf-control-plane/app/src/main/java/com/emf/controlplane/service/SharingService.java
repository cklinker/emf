package com.emf.controlplane.service;

import com.emf.controlplane.dto.*;
import com.emf.controlplane.entity.OrgWideDefault;
import com.emf.controlplane.entity.RecordShare;
import com.emf.controlplane.entity.SharingRule;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.OrgWideDefaultRepository;
import com.emf.controlplane.repository.RecordShareRepository;
import com.emf.controlplane.repository.SharingRuleRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing organization-wide defaults, sharing rules, and record shares.
 */
@Service
public class SharingService {

    private static final Logger log = LoggerFactory.getLogger(SharingService.class);

    private final OrgWideDefaultRepository owdRepository;
    private final SharingRuleRepository sharingRuleRepository;
    private final RecordShareRepository recordShareRepository;
    private final CollectionRepository collectionRepository;

    public SharingService(
            OrgWideDefaultRepository owdRepository,
            SharingRuleRepository sharingRuleRepository,
            RecordShareRepository recordShareRepository,
            CollectionRepository collectionRepository) {
        this.owdRepository = owdRepository;
        this.sharingRuleRepository = sharingRuleRepository;
        this.recordShareRepository = recordShareRepository;
        this.collectionRepository = collectionRepository;
    }

    // ---- OWD operations ----

    @Transactional(readOnly = true)
    public OrgWideDefaultDto getOwd(String collectionId) {
        String tenantId = TenantContextHolder.requireTenantId();
        verifyCollection(collectionId);
        return owdRepository.findByTenantIdAndCollectionId(tenantId, collectionId)
                .map(OrgWideDefaultDto::fromEntity)
                .orElseGet(() -> {
                    // Return default (PUBLIC_READ_WRITE) if none configured
                    OrgWideDefaultDto dto = new OrgWideDefaultDto();
                    dto.setCollectionId(collectionId);
                    dto.setInternalAccess("PUBLIC_READ_WRITE");
                    dto.setExternalAccess("PRIVATE");
                    return dto;
                });
    }

    @Transactional
    public OrgWideDefaultDto setOwd(String collectionId, SetOwdRequest request) {
        String tenantId = TenantContextHolder.requireTenantId();
        verifyCollection(collectionId);

        OrgWideDefault owd = owdRepository.findByTenantIdAndCollectionId(tenantId, collectionId)
                .orElseGet(() -> {
                    OrgWideDefault newOwd = new OrgWideDefault(tenantId, collectionId, request.getInternalAccess());
                    return newOwd;
                });

        owd.setInternalAccess(request.getInternalAccess());
        if (request.getExternalAccess() != null) {
            owd.setExternalAccess(request.getExternalAccess());
        }

        owd = owdRepository.save(owd);
        log.info("Set OWD for collection {} to internal={}, external={}", collectionId,
                owd.getInternalAccess(), owd.getExternalAccess());
        return OrgWideDefaultDto.fromEntity(owd);
    }

    @Transactional(readOnly = true)
    public List<OrgWideDefaultDto> listOwds() {
        String tenantId = TenantContextHolder.requireTenantId();
        return owdRepository.findByTenantId(tenantId).stream()
                .map(OrgWideDefaultDto::fromEntity)
                .collect(Collectors.toList());
    }

    // ---- Sharing Rule operations ----

    @Transactional(readOnly = true)
    public List<SharingRuleDto> listRules(String collectionId) {
        String tenantId = TenantContextHolder.requireTenantId();
        return sharingRuleRepository.findByTenantIdAndCollectionId(tenantId, collectionId).stream()
                .map(SharingRuleDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public SharingRuleDto createRule(String collectionId, CreateSharingRuleRequest request) {
        String tenantId = TenantContextHolder.requireTenantId();
        verifyCollection(collectionId);

        SharingRule rule = new SharingRule();
        rule.setTenantId(tenantId);
        rule.setCollectionId(collectionId);
        rule.setName(request.getName());
        rule.setRuleType(request.getRuleType());
        rule.setSharedFrom(request.getSharedFrom());
        rule.setSharedTo(request.getSharedTo());
        rule.setSharedToType(request.getSharedToType());
        rule.setAccessLevel(request.getAccessLevel());
        rule.setCriteria(request.getCriteria());

        rule = sharingRuleRepository.save(rule);
        log.info("Created sharing rule '{}' for collection {}", rule.getName(), collectionId);
        return SharingRuleDto.fromEntity(rule);
    }

    @Transactional
    public SharingRuleDto updateRule(String ruleId, UpdateSharingRuleRequest request) {
        SharingRule rule = sharingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("SharingRule", ruleId));

        if (request.getName() != null) rule.setName(request.getName());
        if (request.getSharedFrom() != null) rule.setSharedFrom(request.getSharedFrom());
        if (request.getSharedTo() != null) rule.setSharedTo(request.getSharedTo());
        if (request.getSharedToType() != null) rule.setSharedToType(request.getSharedToType());
        if (request.getAccessLevel() != null) rule.setAccessLevel(request.getAccessLevel());
        if (request.getCriteria() != null) rule.setCriteria(request.getCriteria());
        if (request.getActive() != null) rule.setActive(request.getActive());

        rule = sharingRuleRepository.save(rule);
        log.info("Updated sharing rule '{}'", ruleId);
        return SharingRuleDto.fromEntity(rule);
    }

    @Transactional
    public void deleteRule(String ruleId) {
        if (!sharingRuleRepository.existsById(ruleId)) {
            throw new ResourceNotFoundException("SharingRule", ruleId);
        }
        sharingRuleRepository.deleteById(ruleId);
        log.info("Deleted sharing rule '{}'", ruleId);
    }

    // ---- Record Share operations ----

    @Transactional(readOnly = true)
    public List<RecordShareDto> listRecordShares(String collectionId, String recordId) {
        return recordShareRepository.findByCollectionIdAndRecordId(collectionId, recordId).stream()
                .map(RecordShareDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public RecordShareDto createRecordShare(String collectionId, CreateRecordShareRequest request, String createdBy) {
        String tenantId = TenantContextHolder.requireTenantId();

        RecordShare share = new RecordShare();
        share.setTenantId(tenantId);
        share.setCollectionId(collectionId);
        share.setRecordId(request.getRecordId());
        share.setSharedWithId(request.getSharedWithId());
        share.setSharedWithType(request.getSharedWithType());
        share.setAccessLevel(request.getAccessLevel());
        share.setReason(request.getReason());
        share.setCreatedBy(createdBy);

        share = recordShareRepository.save(share);
        log.info("Created record share for record {} in collection {}", request.getRecordId(), collectionId);
        return RecordShareDto.fromEntity(share);
    }

    @Transactional
    public void deleteRecordShare(String shareId) {
        if (!recordShareRepository.existsById(shareId)) {
            throw new ResourceNotFoundException("RecordShare", shareId);
        }
        recordShareRepository.deleteById(shareId);
        log.info("Deleted record share '{}'", shareId);
    }

    private void verifyCollection(String collectionId) {
        if (!collectionRepository.existsById(collectionId)) {
            throw new ResourceNotFoundException("Collection", collectionId);
        }
    }
}
