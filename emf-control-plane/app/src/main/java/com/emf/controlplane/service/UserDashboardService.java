package com.emf.controlplane.service;

import com.emf.controlplane.audit.SetupAudited;
import com.emf.controlplane.dto.CreateDashboardRequest;
import com.emf.controlplane.entity.DashboardComponent;
import com.emf.controlplane.entity.Report;
import com.emf.controlplane.entity.ReportFolder;
import com.emf.controlplane.entity.UserDashboard;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.ReportFolderRepository;
import com.emf.controlplane.repository.ReportRepository;
import com.emf.controlplane.repository.UserDashboardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserDashboardService {

    private static final Logger log = LoggerFactory.getLogger(UserDashboardService.class);

    private final UserDashboardRepository dashboardRepository;
    private final ReportRepository reportRepository;
    private final ReportFolderRepository folderRepository;

    public UserDashboardService(UserDashboardRepository dashboardRepository,
                                ReportRepository reportRepository,
                                ReportFolderRepository folderRepository) {
        this.dashboardRepository = dashboardRepository;
        this.reportRepository = reportRepository;
        this.folderRepository = folderRepository;
    }

    @Transactional(readOnly = true)
    public List<UserDashboard> listDashboards(String tenantId, String userId) {
        if (userId != null) {
            return dashboardRepository.findAccessibleDashboards(tenantId, userId);
        }
        return dashboardRepository.findByTenantIdOrderByNameAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public UserDashboard getDashboard(String id) {
        return dashboardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dashboard", id));
    }

    @Transactional
    @SetupAudited(section = "Dashboards", entityType = "Dashboard")
    public UserDashboard createDashboard(String tenantId, String userId, CreateDashboardRequest request) {
        log.info("Creating dashboard '{}' for tenant: {}", request.getName(), tenantId);

        UserDashboard dashboard = new UserDashboard();
        dashboard.setTenantId(tenantId);
        dashboard.setName(request.getName());
        dashboard.setDescription(request.getDescription());
        dashboard.setAccessLevel(request.getAccessLevel() != null ? request.getAccessLevel() : "PRIVATE");
        dashboard.setDynamic(request.isDynamic());
        dashboard.setRunningUserId(request.getRunningUserId());
        dashboard.setColumnCount(request.getColumnCount());
        dashboard.setCreatedBy(userId);

        if (request.getFolderId() != null) {
            ReportFolder folder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("ReportFolder", request.getFolderId()));
            dashboard.setFolder(folder);
        }

        if (request.getComponents() != null) {
            List<DashboardComponent> components = buildComponents(dashboard, request.getComponents());
            dashboard.setComponents(components);
        }

        return dashboardRepository.save(dashboard);
    }

    @Transactional
    @SetupAudited(section = "Dashboards", entityType = "Dashboard")
    public UserDashboard updateDashboard(String id, CreateDashboardRequest request) {
        log.info("Updating dashboard: {}", id);
        UserDashboard dashboard = getDashboard(id);

        if (request.getName() != null) dashboard.setName(request.getName());
        if (request.getDescription() != null) dashboard.setDescription(request.getDescription());
        if (request.getAccessLevel() != null) dashboard.setAccessLevel(request.getAccessLevel());
        dashboard.setDynamic(request.isDynamic());
        if (request.getRunningUserId() != null) dashboard.setRunningUserId(request.getRunningUserId());
        dashboard.setColumnCount(request.getColumnCount());

        if (request.getFolderId() != null) {
            ReportFolder folder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("ReportFolder", request.getFolderId()));
            dashboard.setFolder(folder);
        }

        if (request.getComponents() != null) {
            dashboard.getComponents().clear();
            List<DashboardComponent> components = buildComponents(dashboard, request.getComponents());
            dashboard.getComponents().addAll(components);
        }

        return dashboardRepository.save(dashboard);
    }

    @Transactional
    @SetupAudited(section = "Dashboards", entityType = "Dashboard")
    public void deleteDashboard(String id) {
        log.info("Deleting dashboard: {}", id);
        UserDashboard dashboard = getDashboard(id);
        dashboardRepository.delete(dashboard);
    }

    private List<DashboardComponent> buildComponents(UserDashboard dashboard,
                                                      List<CreateDashboardRequest.ComponentRequest> requests) {
        List<DashboardComponent> components = new ArrayList<>();
        for (CreateDashboardRequest.ComponentRequest req : requests) {
            Report report = reportRepository.findById(req.getReportId())
                    .orElseThrow(() -> new ResourceNotFoundException("Report", req.getReportId()));

            DashboardComponent comp = new DashboardComponent();
            comp.setDashboard(dashboard);
            comp.setReport(report);
            comp.setComponentType(req.getComponentType());
            comp.setTitle(req.getTitle());
            comp.setColumnPosition(req.getColumnPosition());
            comp.setRowPosition(req.getRowPosition());
            comp.setColumnSpan(req.getColumnSpan());
            comp.setRowSpan(req.getRowSpan());
            comp.setConfig(req.getConfig() != null ? req.getConfig() : "{}");
            comp.setSortOrder(req.getSortOrder());
            components.add(comp);
        }
        return components;
    }
}
