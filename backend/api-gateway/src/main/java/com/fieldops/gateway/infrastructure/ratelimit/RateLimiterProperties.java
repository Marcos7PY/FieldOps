package com.fieldops.gateway.infrastructure.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "fieldops.rate-limiting")
public class RateLimiterProperties {

    private boolean enabled = true;
    private int capacity = 100;
    private int windowSeconds = 60;
    private List<String> trustedProxies = List.of();
    private int maxTrackedClients = 100000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies != null ? trustedProxies : List.of();
    }

    public int getMaxTrackedClients() {
        return maxTrackedClients;
    }

    public void setMaxTrackedClients(int maxTrackedClients) {
        this.maxTrackedClients = maxTrackedClients;
    }
}
