package com.emf.controlplane.service;

import com.emf.controlplane.audit.SetupAudited;
import com.emf.controlplane.dto.CreateListViewRequest;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.ListView;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.ListViewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListViewService {

    private static final Logger log = LoggerFactory.getLogger(ListViewService.class);

    private final ListViewRepository listViewRepository;
    private final CollectionService collectionService;

    public ListViewService(ListViewRepository listViewRepository, CollectionService collectionService) {
        this.listViewRepository = listViewRepository;
        this.collectionService = collectionService;
    }

    @Transactional(readOnly = true)
    public List<ListView> listViews(String tenantId, String collectionId, String userId) {
        if (collectionId == null) {
            return listViewRepository.findByTenantIdOrderByNameAsc(tenantId);
        }
        return listViewRepository.findAccessibleViews(tenantId, collectionId, userId);
    }

    @Transactional(readOnly = true)
    public List<ListView> listAllViews(String tenantId, String collectionId) {
        if (collectionId == null) {
            return listViewRepository.findByTenantIdOrderByNameAsc(tenantId);
        }
        return listViewRepository.findByTenantIdAndCollectionIdOrderByNameAsc(tenantId, collectionId);
    }

    @Transactional(readOnly = true)
    public ListView getView(String id) {
        return listViewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ListView", id));
    }

    @Transactional
    @SetupAudited(section = "List Views", entityType = "ListView")
    public ListView createView(String tenantId, String collectionId, String userId,
                                CreateListViewRequest request) {
        log.info("Creating list view '{}' for collection: {}", request.getName(), collectionId);

        Collection collection = collectionService.getCollection(collectionId);

        if (listViewRepository.existsByTenantIdAndCollectionIdAndNameAndCreatedBy(
                tenantId, collectionId, request.getName(), userId)) {
            throw new DuplicateResourceException("ListView", "name", request.getName());
        }

        ListView view = new ListView(tenantId, collection, request.getName(), userId);
        view.setVisibility(request.getVisibility() != null ? request.getVisibility() : "PRIVATE");
        view.setDefault(request.isDefault());
        view.setColumns(request.getColumns());
        view.setFilterLogic(request.getFilterLogic());
        view.setFilters(request.getFilters() != null ? request.getFilters() : "[]");
        view.setSortField(request.getSortField());
        view.setSortDirection(request.getSortDirection() != null ? request.getSortDirection() : "ASC");
        if (request.getRowLimit() != null) view.setRowLimit(request.getRowLimit());
        view.setChartConfig(request.getChartConfig());

        return listViewRepository.save(view);
    }

    @Transactional
    @SetupAudited(section = "List Views", entityType = "ListView")
    public ListView updateView(String id, CreateListViewRequest request) {
        log.info("Updating list view: {}", id);
        ListView view = getView(id);

        if (request.getName() != null) view.setName(request.getName());
        if (request.getVisibility() != null) view.setVisibility(request.getVisibility());
        if (request.getColumns() != null) view.setColumns(request.getColumns());
        if (request.getFilterLogic() != null) view.setFilterLogic(request.getFilterLogic());
        if (request.getFilters() != null) view.setFilters(request.getFilters());
        if (request.getSortField() != null) view.setSortField(request.getSortField());
        if (request.getSortDirection() != null) view.setSortDirection(request.getSortDirection());
        if (request.getRowLimit() != null) view.setRowLimit(request.getRowLimit());
        if (request.getChartConfig() != null) view.setChartConfig(request.getChartConfig());

        return listViewRepository.save(view);
    }

    @Transactional
    @SetupAudited(section = "List Views", entityType = "ListView")
    public void deleteView(String id) {
        log.info("Deleting list view: {}", id);
        ListView view = getView(id);
        listViewRepository.delete(view);
    }
}
