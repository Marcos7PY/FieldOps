package com.fieldops.orders.application.service;

import com.fieldops.orders.application.dto.EvidenceMetadata;
import com.fieldops.orders.application.dto.EvidenceResponse;
import com.fieldops.orders.domain.exception.AccessDeniedException;
import com.fieldops.orders.domain.exception.BusinessRuleViolationException;
import com.fieldops.orders.domain.model.OrderStatus;
import com.fieldops.orders.domain.model.WorkOrder;
import com.fieldops.orders.domain.model.WorkOrderEvidence;
import com.fieldops.orders.infrastructure.persistence.WorkOrderEvidenceRepository;
import com.fieldops.orders.infrastructure.persistence.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvidenceServiceTest {

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private WorkOrderEvidenceRepository evidenceRepository;

    private final WorkOrderMapper mapper = new WorkOrderMapper();
    private EvidenceService service;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        service = new EvidenceService(workOrderRepository, evidenceRepository, mapper, tempDir.toString());
    }

    private WorkOrder createTestOrder(Long id, OrderStatus status, Long technicianId) {
        WorkOrder order = new WorkOrder();
        order.setId(id);
        order.setStatus(status);
        order.setAssignedTechnicianId(technicianId);
        return order;
    }

    @Test
    void uploadValidEvidenceSavesSuccessfully() {
        WorkOrder order = createTestOrder(1L, OrderStatus.IN_PROGRESS, 50L);
        when(workOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        when(evidenceRepository.save(any(WorkOrderEvidence.class))).thenAnswer(invocation -> {
            WorkOrderEvidence ev = invocation.getArgument(0);
            ev.setId(100L);
            return ev;
        });

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3, 4}
        );

        EvidenceMetadata metadata = new EvidenceMetadata(
                BigDecimal.valueOf(40.7128),
                BigDecimal.valueOf(-74.0060),
                LocalDateTime.now()
        );

        EvidenceResponse response = service.uploadEvidence(1L, file, metadata, 50L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.workOrderId()).isEqualTo(1L);
        assertThat(response.contentType()).isEqualTo("image/jpeg");
        assertThat(response.latitude()).isEqualTo(BigDecimal.valueOf(40.7128));
    }

    @Test
    void uploadInvalidContentTypeThrowsException() {
        WorkOrder order = createTestOrder(1L, OrderStatus.IN_PROGRESS, 50L);
        when(workOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                new byte[]{1, 2, 3, 4}
        );

        assertThatThrownBy(() -> service.uploadEvidence(1L, file, null, 50L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Invalid file type");
    }

    @Test
    void uploadExceeding5MBThrowsException() {
        WorkOrder order = createTestOrder(1L, OrderStatus.IN_PROGRESS, 50L);
        when(workOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        byte[] largeContent = new byte[6 * 1024 * 1024]; // 6 MB
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "huge.png",
                "image/png",
                largeContent
        );

        assertThatThrownBy(() -> service.uploadEvidence(1L, file, null, 50L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void uploadForOtherTechnicianThrowsAccessDenied() {
        WorkOrder order = createTestOrder(1L, OrderStatus.IN_PROGRESS, 50L);
        when(workOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                new byte[]{1, 2}
        );

        assertThatThrownBy(() -> service.uploadEvidence(1L, file, null, 999L))
                .isInstanceOf(AccessDeniedException.class);
    }
}
