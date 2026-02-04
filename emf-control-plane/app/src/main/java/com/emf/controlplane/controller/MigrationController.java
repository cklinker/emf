package com.emf.controlplane.controller;

import com.emf.controlplane.dto.MigrationPlanDto;
import com.emf.controlplane.dto.MigrationRunDto;
import com.emf.controlplane.dto.PlanMigrationRequest;
import com.emf.controlplane.service.MigrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing schema migrations.
 *
 * <p>Provides endpoints for planning migrations and viewing migration history.
 * All endpoints require ADMIN role authorization.
 *
 * <p>Requirements satisfied:
 * <ul>
 *   <li>7.1: Generate a migration plan showing steps to migrate from current to target schema</li>
 *   <li>7.2: Return the history of executed migrations</li>
 *   <li>7.3: Return migration run details including all steps and their status</li>
 * </ul>
 */
@RestController
@RequestMapping("/control/migrations")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Migrations", description = "Schema migration management APIs")
public class MigrationController {

    private static final Logger log = LoggerFactory.getLogger(MigrationController.class);

    private final MigrationService migrationService;

    public MigrationController(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    /**
     * Plans a migration from the current schema to the proposed schema.
     * Generates a list of steps required to transform the current schema to the target.
     * The migration plan is persisted to the database for tracking.
     * Requires ADMIN role authorization.
     *
     * @param request The migration plan request containing collection ID and proposed definition
     * @return The migration plan with generated steps
     *
     * Validates: Requirement 7.1
     */
    @PostMapping("/plan")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Plan a schema migration",
            description = "Generates a migration plan showing the steps required to migrate " +
                    "from the current collection schema to the proposed schema. " +
                    "The plan is persisted for tracking. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Migration plan generated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request - validation errors"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "Collection not found")
    })
    public ResponseEntity<MigrationPlanDto> planMigration(
            @Valid @RequestBody PlanMigrationRequest request) {
        log.info("REST request to plan migration for collection: {}", request.getCollectionId());

        MigrationPlanDto plan = migrationService.planMigration(request);

        return ResponseEntity.ok(plan);
    }

    /**
     * Lists all migration runs ordered by creation date descending.
     * This is a convenience endpoint that delegates to /runs.
     * Requires ADMIN role authorization.
     *
     * @return List of all migration runs
     *
     * Validates: Requirement 7.2
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "List migration runs",
            description = "Returns the history of all migration runs ordered by creation date descending. " +
                    "Each run includes basic information about the migration. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Migration runs retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
    })
    public ResponseEntity<List<MigrationRunDto>> listMigrations() {
        log.info("REST request to list migrations");

        List<MigrationRunDto> runs = migrationService.listMigrationRuns();

        return ResponseEntity.ok(runs);
    }

    /**
     * Lists all migration runs ordered by creation date descending.
     * Requires ADMIN role authorization.
     *
     * @return List of all migration runs
     *
     * Validates: Requirement 7.2
     */
    @GetMapping("/runs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "List migration runs",
            description = "Returns the history of all migration runs ordered by creation date descending. " +
                    "Each run includes basic information about the migration. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Migration runs retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
    })
    public ResponseEntity<List<MigrationRunDto>> listMigrationRuns() {
        log.info("REST request to list migration runs");

        List<MigrationRunDto> runs = migrationService.listMigrationRuns();

        return ResponseEntity.ok(runs);
    }

    /**
     * Retrieves a specific migration run by ID.
     * Returns the full details including all steps and their status.
     * Requires ADMIN role authorization.
     *
     * @param id The migration run ID
     * @return The migration run with all steps
     *
     * Validates: Requirement 7.3
     */
    @GetMapping("/runs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get migration run details",
            description = "Returns the details of a specific migration run including all steps " +
                    "and their status. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Migration run retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "Migration run not found")
    })
    public ResponseEntity<MigrationRunDto> getMigrationRun(
            @Parameter(description = "The migration run ID")
            @PathVariable String id) {
        log.info("REST request to get migration run: {}", id);

        MigrationRunDto run = migrationService.getMigrationRun(id);

        return ResponseEntity.ok(run);
    }
}
