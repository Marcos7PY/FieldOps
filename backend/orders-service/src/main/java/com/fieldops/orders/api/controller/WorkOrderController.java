package com.fieldops.orders.api.controller;

import com.fieldops.orders.application.dto.AssignWorkOrderRequest;
import com.fieldops.orders.application.dto.ChangeStatusRequest;
import com.fieldops.orders.application.dto.CreateWorkOrderRequest;
import com.fieldops.orders.application.dto.EvidenceContent;
import com.fieldops.orders.application.dto.EvidenceMetadata;
import com.fieldops.orders.application.dto.EvidenceResponse;
import com.fieldops.orders.application.dto.PageResponse;
import com.fieldops.orders.application.dto.WorkOrderMetricsResponse;
import com.fieldops.orders.application.dto.WorkOrderMetricsRangeResponse;
import com.fieldops.orders.application.dto.WorkOrderResponse;
import com.fieldops.orders.application.dto.WorkOrderSummaryResponse;
import com.fieldops.orders.application.service.EvidenceService;
import com.fieldops.orders.application.service.WorkOrderService;
import com.fieldops.orders.domain.exception.AccessDeniedException;
import com.fieldops.orders.domain.exception.VersionConflictException;
import com.fieldops.orders.domain.model.OrderStatus;
import com.fieldops.orders.infrastructure.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final CurrentUserProvider currentUserProvider;

    public WorkOrderController(
            WorkOrderService workOrderService,
            EvidenceService evidenceService,
            CurrentUserProvider currentUserProvider
    ) {
        this.workOrderService = workOrderService;
        this.evidenceService = evidenceService;
        this.currentUserProvider = currentUserProvider;
    }

    private Long requireCurrentUserId() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException(
                        "El token no contiene un identificador de usuario válido"));
    }

    private Long requireVersion(String ifMatch) {
        Long v = parseVersion(ifMatch);
        if (v == null) {
            throw new VersionConflictException(
                    "La cabecera If-Match debe contener una versión numérica válida");
        }
        return v;
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_SUPERVISOR')")
    public ResponseEntity<WorkOrderResponse> createWorkOrder(
            @Valid @RequestBody CreateWorkOrderRequest request
    ) {
        Long resolvedUserId = requireCurrentUserId();
        WorkOrderResponse response = workOrderService.createWorkOrder(request, resolvedUserId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_SUPERVISOR', 'ROLE_TECHNICIAN')")
    public ResponseEntity<PageResponse<WorkOrderSummaryResponse>> getWorkOrders(
            @RequestParam(value = "status", required = false) OrderStatus status,
            @RequestParam(value = "technicianId", required = false) Long technicianId,
            @RequestParam(value = "clientId", required = false) Long clientId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "createdAt,desc") String sort
    ) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.min(Math.max(1, size), 100);
        Sort sortOrder = parseSort(sort);
        Pageable pageable = PageRequest.of(boundedPage, boundedSize, sortOrder);
        Long resolvedUserId = requireCurrentUserId();
        boolean isSupervisor = currentUserProvider.isSupervisor();

        PageResponse<WorkOrderSummaryResponse> response = workOrderService.findWorkOrders(
                status, technicianId, clientId, from, to, search, pageable, resolvedUserId, isSupervisor
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ROLE_SUPERVISOR')")
    public ResponseEntity<WorkOrderMetricsResponse> getMetrics() {
        return ResponseEntity.ok(workOrderService.getMetrics());
    }

    @GetMapping("/metrics/range")
    @PreAuthorize("hasRole('ROLE_SUPERVISOR')")
    public ResponseEntity<WorkOrderMetricsRangeResponse> getMetricsInRange(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ResponseEntity.ok(workOrderService.getMetricsInRange(from, to));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_SUPERVISOR', 'ROLE_TECHNICIAN')")
    public ResponseEntity<WorkOrderResponse> getWorkOrderById(
            @PathVariable("id") Long id
    ) {
        Long resolvedUserId = requireCurrentUserId();
        boolean isSupervisor = currentUserProvider.isSupervisor();
        WorkOrderResponse response = workOrderService.getWorkOrderById(id, resolvedUserId, isSupervisor);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasRole('ROLE_SUPERVISOR')")
    public ResponseEntity<WorkOrderResponse> assignWorkOrder(
            @PathVariable("id") Long id,
            @Valid @RequestBody AssignWorkOrderRequest request,
            @RequestHeader(value = "If-Match") String ifMatch
    ) {
        Long expectedVersion = requireVersion(ifMatch);
        Long resolvedSupervisorId = requireCurrentUserId();
        WorkOrderResponse response = workOrderService.assignWorkOrder(id, request, resolvedSupervisorId, expectedVersion);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ROLE_SUPERVISOR', 'ROLE_TECHNICIAN')")
    public ResponseEntity<WorkOrderResponse> changeStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody ChangeStatusRequest request,
            @RequestHeader(value = "If-Match") String ifMatch
    ) {
        Long expectedVersion = requireVersion(ifMatch);
        Long resolvedUserId = requireCurrentUserId();
        boolean isSupervisor = currentUserProvider.isSupervisor();
        WorkOrderResponse response = workOrderService.changeStatus(id, request, resolvedUserId, isSupervisor, expectedVersion);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PostMapping(value = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_TECHNICIAN')")
    public ResponseEntity<EvidenceResponse> uploadEvidence(
            @PathVariable("id") Long id,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "metadata", required = false) @Valid EvidenceMetadata metadata
    ) {
        Long resolvedTechnicianId = requireCurrentUserId();
        EvidenceResponse response = evidenceService.uploadEvidence(id, file, metadata, resolvedTechnicianId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{evidenceId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize("hasAnyRole('ROLE_SUPERVISOR', 'ROLE_TECHNICIAN')")
    public ResponseEntity<List<EvidenceResponse>> getEvidences(
            @PathVariable("id") Long id
    ) {
        Long resolvedUserId = requireCurrentUserId();
        boolean isSupervisor = currentUserProvider.isSupervisor();
        return ResponseEntity.ok(evidenceService.getEvidences(id, resolvedUserId, isSupervisor));
    }

    @GetMapping("/{id}/evidence/{evidenceId}/content")
    @PreAuthorize("hasAnyRole('ROLE_SUPERVISOR', 'ROLE_TECHNICIAN')")
    public ResponseEntity<Resource> getEvidenceContent(
            @PathVariable("id") Long id,
            @PathVariable("evidenceId") Long evidenceId
    ) {
        Long userId = requireCurrentUserId();
        boolean isSupervisor = currentUserProvider.isSupervisor();
        EvidenceContent content = evidenceService.loadEvidenceContent(id, evidenceId, userId, isSupervisor);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, content.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + content.fileName() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(content.resource());
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

    private static final java.util.Set<String> ALLOWED_SORT_PROPERTIES = java.util.Set.of(
            "id", "code", "title", "status", "priority", "createdAt", "updatedAt", "scheduledAt"
    );

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        if (!ALLOWED_SORT_PROPERTIES.contains(property)) {
            property = "createdAt";
        }
        Sort.Direction direction = (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }
}
