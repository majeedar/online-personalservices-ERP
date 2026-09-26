package edu.university.ops.shared.integration;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stub mode: in-memory external-system availability. Stub adapters call
 * {@link #check(ExternalSystem)} before answering, so outages behave like a real
 * connection failure (retries, integration errors, admin alert).
 */
@Component
@ConditionalOnProperty(name = "ops.integration.mode", havingValue = "stub", matchIfMissing = true)
public class SimulatedOutages implements ExternalSystemControl {

    private final Set<ExternalSystem> down = ConcurrentHashMap.newKeySet();

    public void check(ExternalSystem system) {
        if (down.contains(system)) {
            throw new ExternalSystemException(system, "CONNECTION_REFUSED", system + " is not reachable");
        }
    }

    @Override
    public Map<ExternalSystem, Health> status() {
        Map<ExternalSystem, Health> result = new EnumMap<>(ExternalSystem.class);
        for (ExternalSystem s : ExternalSystem.values()) {
            result.put(s, down.contains(s) ? Health.DOWN : Health.UP);
        }
        return result;
    }

    @Override
    public void simulateOutage(ExternalSystem system, boolean isDown) {
        if (isDown) {
            down.add(system);
        } else {
            down.remove(system);
        }
    }
}
