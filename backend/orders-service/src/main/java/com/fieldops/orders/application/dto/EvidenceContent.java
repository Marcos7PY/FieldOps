package com.fieldops.orders.application.dto;

import org.springframework.core.io.Resource;

public record EvidenceContent(Resource resource, String contentType, String fileName) {}
