package com.fieldops.analytics.application.service;

import com.fieldops.analytics.api.dto.RebuildStatusResponse;

import java.util.concurrent.CompletableFuture;

public interface ProjectionRebuildService {

    CompletableFuture<Void> rebuildProjectionAsync();

    void executeRebuild();

    RebuildStatusResponse getRebuildStatus();
}
