package com.emf.gateway.config;

/**
 * Governor limit configuration for a tenant, received from the control plane bootstrap.
 * Used by the gateway to enforce per-tenant rate limiting.
 */
public class GovernorLimitConfig {

    private int apiCallsPerDay;

    public GovernorLimitConfig() {
    }

    public GovernorLimitConfig(int apiCallsPerDay) {
        this.apiCallsPerDay = apiCallsPerDay;
    }

    public int getApiCallsPerDay() {
        return apiCallsPerDay;
    }

    public void setApiCallsPerDay(int apiCallsPerDay) {
        this.apiCallsPerDay = apiCallsPerDay;
    }

    @Override
    public String toString() {
        return "GovernorLimitConfig{" +
               "apiCallsPerDay=" + apiCallsPerDay +
               '}';
    }
}
