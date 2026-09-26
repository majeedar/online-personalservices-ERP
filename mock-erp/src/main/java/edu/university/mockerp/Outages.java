package edu.university.mockerp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Simulated outages per system: a "down" system answers 503 (AGENT.md §63). */
@Component
public class Outages {

    public static final Set<String> SYSTEMS = Set.of("PERSONNEL_ERP", "FINANCE_ERP", "TRAVEL_ERP");

    private final Set<String> down = ConcurrentHashMap.newKeySet();

    public void check(String system) {
        if (down.contains(system)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, system + " is under maintenance");
        }
    }

    public void set(String system, boolean isDown) {
        if (!SYSTEMS.contains(system)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown system " + system);
        }
        if (isDown) {
            down.add(system);
        } else {
            down.remove(system);
        }
    }

    public Map<String, String> status() {
        Map<String, String> result = new LinkedHashMap<>();
        SYSTEMS.stream().sorted().forEach(s -> result.put(s, down.contains(s) ? "DOWN" : "UP"));
        return result;
    }
}
