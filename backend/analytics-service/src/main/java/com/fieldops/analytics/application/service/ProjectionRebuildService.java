package com.fieldops.analytics.application.service;

import java.util.concurrent.CompletableFuture;

public interface ProjectionRebuildService {

    CompletableFuture<Void> rebuildProjectionAsync();

    void executeRebuild();
}
