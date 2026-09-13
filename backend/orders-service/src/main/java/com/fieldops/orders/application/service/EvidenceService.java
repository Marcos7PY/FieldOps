package com.fieldops.orders.application.service;

import com.fieldops.orders.application.dto.EvidenceMetadata;
import com.fieldops.orders.application.dto.EvidenceResponse;
import com.fieldops.orders.domain.exception.AccessDeniedException;
import com.fieldops.orders.domain.exception.BusinessRuleViolationException;
import com.fieldops.orders.domain.exception.ResourceNotFoundException;
import com.fieldops.orders.domain.model.OrderStatus;
import com.fieldops.orders.domain.model.WorkOrder;
import com.fieldops.orders.domain.model.WorkOrderEvidence;
import com.fieldops.orders.infrastructure.persistence.WorkOrderEvidenceRepository;
import com.fieldops.orders.infrastructure.persistence.WorkOrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class EvidenceService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderEvidenceRepository evidenceRepository;
    private final WorkOrderMapper mapper;
    private final Path uploadsLocation;

    public EvidenceService(
            WorkOrderRepository workOrderRepository,
            WorkOrderEvidenceRepository evidenceRepository,
            WorkOrderMapper mapper,
            @Value("${fieldops.uploads-path:uploads}") String uploadsPath
    ) {
        this.workOrderRepository = workOrderRepository;
        this.evidenceRepository = evidenceRepository;
        this.mapper = mapper;
        this.uploadsLocation = Paths.get(uploadsPath).toAbsolutePath().normalize();
        initStorage();
    }

    private void initStorage() {
        try {
            Files.createDirectories(uploadsLocation);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize storage directory", e);
        }
    }

    public EvidenceResponse uploadEvidence(
            Long workOrderId,
            MultipartFile file,
            EvidenceMetadata metadata,
            Long technicianId
    ) {
        WorkOrder order = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Work order not found with id: " + workOrderId));

        if (order.getAssignedTechnicianId() == null || !order.getAssignedTechnicianId().equals(technicianId)) {
            throw new AccessDeniedException("Technician can only upload evidence to their assigned order");
        }

        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessRuleViolationException("Cannot add evidence to an order in status " + order.getStatus());
        }

        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
        } else {
            extension = switch (file.getContentType()) {
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                default -> ".jpg";
            };
        }

        String storedFileName = UUID.randomUUID() + extension;
        Path destination = uploadsLocation.resolve(storedFileName).normalize();

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file", e);
        }

        LocalDateTime now = LocalDateTime.now();
        WorkOrderEvidence evidence = new WorkOrderEvidence();
        evidence.setWorkOrder(order);
        evidence.setFilePath("/uploads/" + storedFileName);
        evidence.setContentType(file.getContentType());
        evidence.setSizeBytes(file.getSize());
        evidence.setLatitude(metadata != null ? metadata.latitude() : null);
        evidence.setLongitude(metadata != null ? metadata.longitude() : null);
        evidence.setCapturedAt(metadata != null && metadata.capturedAt() != null ? metadata.capturedAt() : now);
        evidence.setUploadedAt(now);

        WorkOrderEvidence savedEvidence = evidenceRepository.save(evidence);
        order.getEvidences().add(savedEvidence);

        return mapper.toEvidenceResponse(savedEvidence);
    }

    @Transactional(readOnly = true)
    public List<EvidenceResponse> getEvidences(Long workOrderId, Long userId, boolean isSupervisor) {
        WorkOrder order = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Work order not found with id: " + workOrderId));

        if (!isSupervisor) {
            if (order.getAssignedTechnicianId() == null || !order.getAssignedTechnicianId().equals(userId)) {
                throw new AccessDeniedException("Technician can only view evidence of assigned orders");
            }
        }

        return evidenceRepository.findByWorkOrderId(workOrderId).stream()
                .map(mapper::toEvidenceResponse)
                .toList();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleViolationException("Evidence file must not be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessRuleViolationException("Evidence file size exceeds maximum limit of 5 MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessRuleViolationException("Invalid file type: " + contentType + ". Allowed: JPEG, PNG, WEBP");
        }
    }
}