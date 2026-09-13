package com.fieldops.orders.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateClientRequest(
        @NotBlank @Size(max = 150) String businessName,
        @NotBlank @Size(max = 20) String taxId,
        @Size(max = 250) String address,
        BigDecimal latitude,
        BigDecimal longitude,
        @Size(max = 20) String phone
) {}
