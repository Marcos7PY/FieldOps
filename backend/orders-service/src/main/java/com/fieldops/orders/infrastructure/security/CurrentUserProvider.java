package com.fieldops.orders.infrastructure.security;

import java.util.Optional;

public interface CurrentUserProvider {

    Optional<Long> getCurrentUserId();

    Optional<String> getCurrentUsername();

    boolean isSupervisor();

    boolean isTechnician();
}
