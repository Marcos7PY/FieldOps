package com.fieldops.orders.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EvidenceMetadata(
        @DecimalMin(value = "-90.0", message = "La latitud debe ser >= -90.0")
        @DecimalMax(value = "90.0", message = "La latitud debe ser <= 90.0")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "La longitud debe ser >= -180.0")
        @DecimalMax(value = "180.0", message = "La longitud debe ser <= 180.0")
        BigDecimal longitude,

        @PastOrPresent(message = "La fecha de captura no puede ser en el futuro")
        LocalDateTime capturedAt
) {}
