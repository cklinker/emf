package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateScheduledJobRequest;
import com.emf.controlplane.dto.JobExecutionLogDto;
import com.emf.controlplane.dto.ScheduledJobDto;
import com.emf.controlplane.service.ScheduledJobService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/control/scheduled-jobs")
public class ScheduledJobController {

    private final ScheduledJobService scheduledJobService;

    public ScheduledJobController(ScheduledJobService scheduledJobService) {
        this.scheduledJobService = scheduledJobService;
    }

    @GetMapping
    public List<ScheduledJobDto> listJobs(@RequestParam String tenantId) {
        return scheduledJobService.listJobs(tenantId).stream()
                .map(ScheduledJobDto::fromEntity).toList();
    }

    @GetMapping("/{id}")
    public ScheduledJobDto getJob(@PathVariable String id) {
        return ScheduledJobDto.fromEntity(scheduledJobService.getJob(id));
    }

    @PostMapping
    public ResponseEntity<ScheduledJobDto> createJob(
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestBody CreateScheduledJobRequest request) {
        var job = scheduledJobService.createJob(tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ScheduledJobDto.fromEntity(job));
    }

    @PutMapping("/{id}")
    public ScheduledJobDto updateJob(
            @PathVariable String id,
            @RequestBody CreateScheduledJobRequest request) {
        return ScheduledJobDto.fromEntity(scheduledJobService.updateJob(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable String id) {
        scheduledJobService.deleteJob(id);
        return ResponseEntity.noContent().build();
    }

    // --- Execution Logs ---

    @GetMapping("/{id}/logs")
    public List<JobExecutionLogDto> listExecutionLogs(@PathVariable String id) {
        return scheduledJobService.listExecutionLogs(id).stream()
                .map(JobExecutionLogDto::fromEntity).toList();
    }
}
