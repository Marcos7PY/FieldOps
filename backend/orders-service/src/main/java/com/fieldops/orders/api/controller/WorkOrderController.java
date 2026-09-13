package com.fieldops.orders.api.controller;

import com.fieldops.orders.application.dto.AssignWorkOrderRequest;
import com.fieldops.orders.application.dto.ChangeStatusRequest;
import com.fieldops.orders.application.dto.CreateWorkOrderRequest;
import com.fieldops.orders.application.dto.EvidenceMetadata;
import com.fieldops.orders.application.dto.EvidenceResponse;
import com.fieldops.orders.application.dto.PageResponse;
import com.fieldops.orders.application.dto.WorkOrderMetricsResponse;
import com.fieldops.orders.application.dto.WorkOrderResponse;
import com.fieldops.orders.application.dto.WorkOrderSummaryResponse;
import com.fieldops.orders.application.service.EvidenceService;
import com.fieldops.orders.application.service.WorkOrderService;
import com.fieldops.orders.domain.exception.VersionConflictException;
import com.fieldops.orders.domain.model.OrderStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;
    private final EvidenceService evidenceService;

    public WorkOrderController(WorkOrderService workOrderService, EvidenceService evidenceService) {
        this.workOrderService = workOrderService;
        this.evidenceService = evidenceService;
    }

    @PostMapping
    public ResponseEntity<WorkOrderResponse> createWorkOrder(
            @Valid @RequestBody CreateWorkOrderRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId
    ) {
        WorkOrderResponse response = workOrderService.createWorkOrder(request, userId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<WorkOrderSummaryResponse>> getWorkOrders(
            @RequestParam(value = "status", required = false) OrderStatus status,
            @RequestParam(value = "technicianId", required = false) Long technicianId,
            @RequestParam(value = "clientId", required = false) Long clientId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "createdAt,desc") String sort,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "ROLE_SUPERVISOR") String role
    ) {
        int boundedSize = Math.min(Math.max(1, size), 100);
        Sort sortOrder = parseSort(sort);
        Pageable pageable = PageRequest.of(page, boundedSize, sortOrder);
        boolean isSupervisor = "ROLE_SUPERVISOR".equalsIgnoreCase(role);

        PageResponse<WorkOrderSummaryResponse> response = workOrderService.findWorkOrders(
                status, technicianId, clientId, from, to, search, pageable, userId, isSupervisor
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/metrics")
    public ResponseEntity<WorkOrderMetricsResponse> getMetrics() {
        return ResponseEntity.ok(workOrderService.getMetrics());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkOrderResponse> getWorkOrderById(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "ROLE_SUPERVISOR") String role
    ) {
        boolean isSupervisor = "ROLE_SUPERVISOR".equalsIgnoreCase(role);
        WorkOrderResponse response = workOrderService.getWorkOrderById(id, userId, isSupervisor);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<WorkOrderResponse> assignWorkOrder(
            @PathVariable("id") Long id,
            @Valid @RequestBody AssignWorkOrderRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long supervisorId
    ) {
        Long expectedVersion = parseVersion(ifMatch);
        WorkOrderResponse response = workOrderService.assignWorkOrder(id, request, supervisorId, expectedVersion);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<WorkOrderResponse> changeStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody ChangeStatusRequest request,
            @RequestHeader(value = "If-Match") String ifMatch,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "ROLE_SUPERVISOR") String role
    ) {
        Long expectedVersion = parseVersion(ifMatch);
        if (expectedVersion == null) {
            throw new VersionConflictException("If-Match header with numeric version is required");
        }
        boolean isSupervisor = "ROLE_SUPERVISOR".equalsIgnoreCase(role);
        WorkOrderResponse response = workOrderService.changeStatus(id, request, userId, isSupervisor, expectedVersion);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PostMapping(value = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EvidenceResponse> uploadEvidence(
            @PathVariable("id") Long id,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "metadata", required = false) EvidenceMetadata metadata,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long technicianId
    ) {
        EvidenceResponse response = evidenceService.uploadEvidence(id, file, metadata, technicianId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{evidenceId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}/evidence")
    public ResponseEntity<List<EvidenceResponse>> getEvidences(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "ROLE_SUPERVISOR") String role
    ) {
        boolean isSupervisor = "ROLE_SUPERVISOR".equalsIgnoreCase(role);
        return ResponseEntity.ok(evidenceService.getEvidences(id, userId, isSupervisor));
    }

    private Long parseVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        String cleaned = ifMatch.replace("\"", "").replace("W/", "").trim();
        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }
}