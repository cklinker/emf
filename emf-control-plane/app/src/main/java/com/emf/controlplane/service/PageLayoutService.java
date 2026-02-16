package com.emf.controlplane.service;

import com.emf.controlplane.audit.SetupAudited;
import com.emf.controlplane.dto.CreatePageLayoutRequest;
import com.emf.controlplane.entity.*;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.controlplane.repository.LayoutAssignmentRepository;
import com.emf.controlplane.repository.PageLayoutRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PageLayoutService {

    private static final Logger log = LoggerFactory.getLogger(PageLayoutService.class);

    private final PageLayoutRepository layoutRepository;
    private final LayoutAssignmentRepository assignmentRepository;
    private final CollectionService collectionService;
    private final FieldRepository fieldRepository;

    public PageLayoutService(PageLayoutRepository layoutRepository,
                             LayoutAssignmentRepository assignmentRepository,
                             CollectionService collectionService,
                             FieldRepository fieldRepository) {
        this.layoutRepository = layoutRepository;
        this.assignmentRepository = assignmentRepository;
        this.collectionService = collectionService;
        this.fieldRepository = fieldRepository;
    }

    @Transactional(readOnly = true)
    public List<PageLayout> listLayouts(String tenantId, String collectionId) {
        if (collectionId == null) {
            return layoutRepository.findByTenantIdOrderByNameAsc(tenantId);
        }
        return layoutRepository.findByTenantIdAndCollectionIdOrderByNameAsc(tenantId, collectionId);
    }

    @Transactional(readOnly = true)
    public PageLayout getLayout(String id) {
        return layoutRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PageLayout", id));
    }

    @Transactional
    @SetupAudited(section = "Page Layouts", entityType = "PageLayout")
    public PageLayout createLayout(String tenantId, String collectionId, CreatePageLayoutRequest request) {
        log.info("Creating page layout '{}' for collection: {}", request.getName(), collectionId);

        Collection collection = collectionService.getCollection(collectionId);

        if (layoutRepository.existsByTenantIdAndCollectionIdAndName(tenantId, collectionId, request.getName())) {
            throw new DuplicateResourceException("PageLayout", "name", request.getName());
        }

        validateRequest(request);

        PageLayout layout = new PageLayout(tenantId, collection, request.getName());
        layout.setDescription(request.getDescription());
        layout.setLayoutType(request.getLayoutType() != null ? request.getLayoutType() : "DETAIL");
        layout.setDefault(request.isDefault());

        if (request.isDefault()) {
            clearDefaultLayout(tenantId, collectionId);
        }

        layout = layoutRepository.save(layout);

        if (request.getSections() != null) {
            for (CreatePageLayoutRequest.SectionRequest sectionReq : request.getSections()) {
                LayoutSection section = buildSection(layout, sectionReq);
                layout.getSections().add(section);
            }
        }

        if (request.getRelatedLists() != null) {
            for (CreatePageLayoutRequest.RelatedListRequest rlReq : request.getRelatedLists()) {
                LayoutRelatedList rl = buildRelatedList(layout, rlReq);
                layout.getRelatedLists().add(rl);
            }
        }

        return layoutRepository.save(layout);
    }

    @Transactional
    @SetupAudited(section = "Page Layouts", entityType = "PageLayout")
    public PageLayout updateLayout(String id, CreatePageLayoutRequest request) {
        log.info("Updating page layout: {}", id);
        PageLayout layout = getLayout(id);

        if (request.getName() != null && !request.getName().equals(layout.getName())) {
            if (layoutRepository.existsByTenantIdAndCollectionIdAndName(
                    layout.getTenantId(), layout.getCollection().getId(), request.getName())) {
                throw new DuplicateResourceException("PageLayout", "name", request.getName());
            }
            layout.setName(request.getName());
        }
        if (request.getDescription() != null) layout.setDescription(request.getDescription());
        if (request.getLayoutType() != null) layout.setLayoutType(request.getLayoutType());

        if (request.isDefault() && !layout.isDefault()) {
            clearDefaultLayout(layout.getTenantId(), layout.getCollection().getId());
            layout.setDefault(true);
        }

        validateRequest(request);

        // Replace sections if provided
        if (request.getSections() != null) {
            layout.getSections().clear();
            for (CreatePageLayoutRequest.SectionRequest sectionReq : request.getSections()) {
                LayoutSection section = buildSection(layout, sectionReq);
                layout.getSections().add(section);
            }
        }

        if (request.getRelatedLists() != null) {
            layout.getRelatedLists().clear();
            for (CreatePageLayoutRequest.RelatedListRequest rlReq : request.getRelatedLists()) {
                LayoutRelatedList rl = buildRelatedList(layout, rlReq);
                layout.getRelatedLists().add(rl);
            }
        }

        return layoutRepository.save(layout);
    }

    @Transactional
    @SetupAudited(section = "Page Layouts", entityType = "PageLayout")
    public void deleteLayout(String id) {
        log.info("Deleting page layout: {}", id);
        PageLayout layout = getLayout(id);
        assignmentRepository.deleteByLayoutId(id);
        layoutRepository.delete(layout);
    }

    // --- Layout Assignment ---

    @Transactional(readOnly = true)
    public List<LayoutAssignment> listAssignments(String tenantId, String collectionId) {
        return assignmentRepository.findByTenantIdAndCollectionId(tenantId, collectionId);
    }

    @Transactional
    public LayoutAssignment assignLayout(String tenantId, String collectionId,
                                          String profileId, String recordTypeId, String layoutId) {
        log.info("Assigning layout {} to profile {} / recordType {} for collection {}",
                layoutId, profileId, recordTypeId, collectionId);

        PageLayout layout = getLayout(layoutId);
        Collection collection = collectionService.getCollection(collectionId);

        // Upsert: find existing or create new
        LayoutAssignment assignment;
        if (recordTypeId != null) {
            assignment = assignmentRepository
                    .findByTenantIdAndCollectionIdAndProfileIdAndRecordTypeId(
                            tenantId, collectionId, profileId, recordTypeId)
                    .orElse(new LayoutAssignment());
        } else {
            assignment = assignmentRepository
                    .findByTenantIdAndCollectionIdAndProfileIdAndRecordTypeIdIsNull(
                            tenantId, collectionId, profileId)
                    .orElse(new LayoutAssignment());
        }

        assignment.setTenantId(tenantId);
        assignment.setCollection(collection);
        assignment.setProfileId(profileId);
        assignment.setRecordTypeId(recordTypeId);
        assignment.setLayout(layout);

        return assignmentRepository.save(assignment);
    }

    @Transactional(readOnly = true)
    public PageLayout getLayoutForUser(String tenantId, String collectionId,
                                        String recordTypeId, String profileId) {
        // Try exact match: profile + record type
        if (recordTypeId != null) {
            var assignment = assignmentRepository
                    .findByTenantIdAndCollectionIdAndProfileIdAndRecordTypeId(
                            tenantId, collectionId, profileId, recordTypeId);
            if (assignment.isPresent()) {
                return assignment.get().getLayout();
            }
        }

        // Fallback: profile with no record type
        var defaultAssignment = assignmentRepository
                .findByTenantIdAndCollectionIdAndProfileIdAndRecordTypeIdIsNull(
                        tenantId, collectionId, profileId);
        if (defaultAssignment.isPresent()) {
            return defaultAssignment.get().getLayout();
        }

        // Final fallback: default layout for collection
        return layoutRepository.findByTenantIdAndCollectionIdAndIsDefaultTrue(tenantId, collectionId)
                .orElse(null);
    }

    private void clearDefaultLayout(String tenantId, String collectionId) {
        layoutRepository.findByTenantIdAndCollectionIdAndIsDefaultTrue(tenantId, collectionId)
                .ifPresent(existing -> {
                    existing.setDefault(false);
                    layoutRepository.save(existing);
                });
    }

    private void validateRequest(CreatePageLayoutRequest request) {
        if (request.getSections() != null) {
            long highlightsPanelCount = request.getSections().stream()
                    .filter(s -> "HIGHLIGHTS_PANEL".equals(s.getSectionType()))
                    .count();
            if (highlightsPanelCount > 1) {
                throw new IllegalArgumentException("A layout can have at most one Highlights Panel section");
            }

            for (CreatePageLayoutRequest.SectionRequest section : request.getSections()) {
                if (section.getColumns() < 1 || section.getColumns() > 3) {
                    throw new IllegalArgumentException("Section columns must be between 1 and 3");
                }
                boolean hasTabGroup = section.getTabGroup() != null && !section.getTabGroup().isBlank();
                boolean hasTabLabel = section.getTabLabel() != null && !section.getTabLabel().isBlank();
                if (hasTabGroup && !hasTabLabel) {
                    throw new IllegalArgumentException("Tab label is required when tab group is set");
                }
            }
        }
    }

    private LayoutSection buildSection(PageLayout layout, CreatePageLayoutRequest.SectionRequest sectionReq) {
        LayoutSection section = new LayoutSection();
        section.setLayout(layout);
        section.setHeading(sectionReq.getHeading());
        section.setColumns(sectionReq.getColumns());
        section.setSortOrder(sectionReq.getSortOrder());
        section.setCollapsed(sectionReq.isCollapsed());
        section.setStyle(sectionReq.getStyle() != null ? sectionReq.getStyle() : "DEFAULT");
        section.setSectionType(sectionReq.getSectionType() != null ? sectionReq.getSectionType() : "STANDARD");
        section.setTabGroup(sectionReq.getTabGroup());
        section.setTabLabel(sectionReq.getTabLabel());
        section.setVisibilityRule(sectionReq.getVisibilityRule());

        if (sectionReq.getFields() != null) {
            for (CreatePageLayoutRequest.FieldPlacementRequest fieldReq : sectionReq.getFields()) {
                LayoutField lf = buildLayoutField(section, fieldReq);
                section.getFields().add(lf);
            }
        }
        return section;
    }

    private LayoutField buildLayoutField(LayoutSection section, CreatePageLayoutRequest.FieldPlacementRequest fieldReq) {
        Field field = fieldRepository.findById(fieldReq.getFieldId())
                .orElseThrow(() -> new ResourceNotFoundException("Field", fieldReq.getFieldId()));
        LayoutField lf = new LayoutField();
        lf.setSection(section);
        lf.setField(field);
        lf.setColumnNumber(fieldReq.getColumnNumber());
        lf.setSortOrder(fieldReq.getSortOrder());
        lf.setRequiredOnLayout(fieldReq.isRequiredOnLayout());
        lf.setReadOnlyOnLayout(fieldReq.isReadOnlyOnLayout());
        lf.setLabelOverride(fieldReq.getLabelOverride());
        lf.setHelpTextOverride(fieldReq.getHelpTextOverride());
        lf.setVisibilityRule(fieldReq.getVisibilityRule());
        return lf;
    }

    private LayoutRelatedList buildRelatedList(PageLayout layout, CreatePageLayoutRequest.RelatedListRequest rlReq) {
        LayoutRelatedList rl = new LayoutRelatedList();
        rl.setLayout(layout);
        Collection relatedColl = collectionService.getCollection(rlReq.getRelatedCollectionId());
        rl.setRelatedCollection(relatedColl);
        Field relField = fieldRepository.findById(rlReq.getRelationshipFieldId())
                .orElseThrow(() -> new ResourceNotFoundException("Field", rlReq.getRelationshipFieldId()));
        rl.setRelationshipField(relField);
        rl.setDisplayColumns(rlReq.getDisplayColumns());
        rl.setSortField(rlReq.getSortField());
        rl.setSortDirection(rlReq.getSortDirection() != null ? rlReq.getSortDirection() : "DESC");
        rl.setRowLimit(rlReq.getRowLimit());
        rl.setSortOrder(rlReq.getSortOrder());
        return rl;
    }
}
