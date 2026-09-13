package com.fieldops.orders.application.dto;

import java.math.BigDecimal;

public record ClientResponse(
        Long id,
        String businessName,
        String taxId,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String phone,
        boolean active
) {}
