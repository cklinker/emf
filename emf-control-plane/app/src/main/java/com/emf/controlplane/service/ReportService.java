package com.emf.controlplane.service;

import com.emf.controlplane.audit.SetupAudited;
import com.emf.controlplane.dto.CreateReportRequest;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Report;
import com.emf.controlplane.entity.ReportFolder;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.ReportFolderRepository;
import com.emf.controlplane.repository.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reportRepository;
    private final ReportFolderRepository folderRepository;
    private final CollectionService collectionService;

    public ReportService(ReportRepository reportRepository,
                         ReportFolderRepository folderRepository,
                         CollectionService collectionService) {
        this.reportRepository = reportRepository;
        this.folderRepository = folderRepository;
        this.collectionService = collectionService;
    }

    @Transactional(readOnly = true)
    public List<Report> listReports(String tenantId, String userId) {
        return reportRepository.findAccessibleReports(tenantId, userId);
    }

    @Transactional(readOnly = true)
    public List<Report> listAllReports(String tenantId) {
        return reportRepository.findByTenantIdOrderByNameAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public Report getReport(String id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report", id));
    }

    @Transactional
    @SetupAudited(section = "Reports", entityType = "Report")
    public Report createReport(String tenantId, String userId, CreateReportRequest request) {
        log.info("Creating report '{}' for tenant: {}", request.getName(), tenantId);

        Collection collection = collectionService.getCollection(request.getPrimaryCollectionId());

        Report report = new Report();
        report.setTenantId(tenantId);
        report.setName(request.getName());
        report.setDescription(request.getDescription());
        report.setReportType(request.getReportType());
        report.setPrimaryCollection(collection);
        report.setRelatedJoins(request.getRelatedJoins() != null ? request.getRelatedJoins() : "[]");
        report.setColumns(request.getColumns());
        report.setFilters(request.getFilters() != null ? request.getFilters() : "[]");
        report.setFilterLogic(request.getFilterLogic());
        report.setRowGroupings(request.getRowGroupings() != null ? request.getRowGroupings() : "[]");
        report.setColumnGroupings(request.getColumnGroupings() != null ? request.getColumnGroupings() : "[]");
        report.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : "[]");
        report.setChartType(request.getChartType());
        report.setChartConfig(request.getChartConfig());
        report.setScope(request.getScope() != null ? request.getScope() : "MY_RECORDS");
        report.setAccessLevel(request.getAccessLevel() != null ? request.getAccessLevel() : "PRIVATE");
        report.setCreatedBy(userId);

        if (request.getFolderId() != null) {
            ReportFolder folder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("ReportFolder", request.getFolderId()));
            report.setFolder(folder);
        }

        return reportRepository.save(report);
    }

    @Transactional
    @SetupAudited(section = "Reports", entityType = "Report")
    public Report updateReport(String id, CreateReportRequest request) {
        log.info("Updating report: {}", id);
        Report report = getReport(id);

        if (request.getName() != null) report.setName(request.getName());
        if (request.getDescription() != null) report.setDescription(request.getDescription());
        if (request.getReportType() != null) report.setReportType(request.getReportType());
        if (request.getColumns() != null) report.setColumns(request.getColumns());
        if (request.getFilters() != null) report.setFilters(request.getFilters());
        if (request.getFilterLogic() != null) report.setFilterLogic(request.getFilterLogic());
        if (request.getRowGroupings() != null) report.setRowGroupings(request.getRowGroupings());
        if (request.getColumnGroupings() != null) report.setColumnGroupings(request.getColumnGroupings());
        if (request.getSortOrder() != null) report.setSortOrder(request.getSortOrder());
        if (request.getChartType() != null) report.setChartType(request.getChartType());
        if (request.getChartConfig() != null) report.setChartConfig(request.getChartConfig());
        if (request.getScope() != null) report.setScope(request.getScope());
        if (request.getAccessLevel() != null) report.setAccessLevel(request.getAccessLevel());
        if (request.getRelatedJoins() != null) report.setRelatedJoins(request.getRelatedJoins());

        if (request.getFolderId() != null) {
            ReportFolder folder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("ReportFolder", request.getFolderId()));
            report.setFolder(folder);
        }

        if (request.getPrimaryCollectionId() != null) {
            Collection collection = collectionService.getCollection(request.getPrimaryCollectionId());
            report.setPrimaryCollection(collection);
        }

        return reportRepository.save(report);
    }

    @Transactional
    @SetupAudited(section = "Reports", entityType = "Report")
    public void deleteReport(String id) {
        log.info("Deleting report: {}", id);
        Report report = getReport(id);
        reportRepository.delete(report);
    }

    // --- Folders ---

    @Transactional(readOnly = true)
    public List<ReportFolder> listFolders(String tenantId) {
        return folderRepository.findByTenantIdOrderByNameAsc(tenantId);
    }

    @Transactional
    public ReportFolder createFolder(String tenantId, String userId, String name, String accessLevel) {
        ReportFolder folder = new ReportFolder();
        folder.setTenantId(tenantId);
        folder.setName(name);
        folder.setCreatedBy(userId);
        folder.setAccessLevel(accessLevel != null ? accessLevel : "PRIVATE");
        return folderRepository.save(folder);
    }

    @Transactional
    public void deleteFolder(String id) {
        ReportFolder folder = folderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReportFolder", id));
        folderRepository.delete(folder);
    }
}
