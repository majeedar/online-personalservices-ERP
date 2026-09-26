package edu.university.ops.shared.integration;

import java.util.Map;

/**
 * Status of the external systems and, for demonstrations, simulated outages
 * (AGENT.md §63 "configurable mock failure"). In stub mode the flags are
 * in-memory; in http mode they are forwarded to the mock-erp service.
 */
public interface ExternalSystemControl {

    enum Health { UP, DOWN }

    Map<ExternalSystem, Health> status();

    void simulateOutage(ExternalSystem system, boolean down);
}
