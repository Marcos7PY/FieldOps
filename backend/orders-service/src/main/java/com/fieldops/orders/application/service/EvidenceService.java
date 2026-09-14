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
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class EvidenceService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png",  ".png",
            "image/webp", ".webp"
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
        String detectedContentType = detectContentType(file);

        String extension = EXTENSION_BY_CONTENT_TYPE.get(detectedContentType);
        if (extension == null) {
            throw new BusinessRuleViolationException("Tipo de contenido no admitido: " + detectedContentType);
        }

        String storedFileName = UUID.randomUUID() + extension;
        Path destination = uploadsLocation.resolve(storedFileName).normalize();

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file", e);
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        try {
                            Files.deleteIfExists(destination);
                        } catch (IOException ignored) {
                        }
                    }
                }
            });
        }

        LocalDateTime now = LocalDateTime.now();
        WorkOrderEvidence evidence = new WorkOrderEvidence();
        evidence.setWorkOrder(order);
        evidence.setFilePath(storedFileName);
        evidence.setContentType(detectedContentType);
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

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> loadEvidenceContent(Long workOrderId, Long evidenceId, Long userId, boolean isSupervisor) {
        WorkOrder order = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Work order not found with id: " + workOrderId));

        if (!isSupervisor) {
            if (order.getAssignedTechnicianId() == null || !order.getAssignedTechnicianId().equals(userId)) {
                throw new AccessDeniedException("Technician can only view evidence of assigned orders");
            }
        }

        WorkOrderEvidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence not found with id: " + evidenceId));

        if (!evidence.getWorkOrder().getId().equals(workOrderId)) {
            throw new ResourceNotFoundException("Evidence not associated with work order: " + workOrderId);
        }

        Path filePath = uploadsLocation.resolve(evidence.getFilePath()).normalize();
        if (!filePath.startsWith(uploadsLocation)) {
            throw new AccessDeniedException("Ruta de evidencia fuera del directorio permitido");
        }
        if (!Files.exists(filePath)) {
            throw new ResourceNotFoundException("File not found: " + evidence.getFilePath());
        }

        Resource resource = new FileSystemResource(filePath);
        String contentType = evidence.getContentType() != null ? evidence.getContentType() : "application/octet-stream";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filePath.getFileName().toString() + "\"")
                .body(resource);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleViolationException("Evidence file must not be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessRuleViolationException("Evidence file size exceeds maximum limit of 5 MB");
        }
    }

    private String detectContentType(MultipartFile file) {
        byte[] header = new byte[12];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(header, 0, 12);
            if (read < 12) {
                throw new BusinessRuleViolationException("Archivo de evidencia corrupto o demasiado pequeño");
            }
        } catch (IOException e) {
            throw new BusinessRuleViolationException("No se pudo leer el archivo de evidencia");
        }

        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return "image/png";
        }
        if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "image/webp";
        }
        throw new BusinessRuleViolationException(
                "El contenido del archivo no corresponde a una imagen JPEG, PNG o WEBP");
    }
}
