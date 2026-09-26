package edu.university.mockerp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The mock endpoints of AGENT.md §23. Field names imitate a legacy ERP on purpose
 * (PERS_NR, KOSTL, BELNR, ...): Online Personalservices must map them (§24).
 * Postings and exports are idempotent by the {@code Idempotency-Key} header (§29).
 */
@RestController
class MockErpController {

    private static final Logger log = LoggerFactory.getLogger(MockErpController.class);

    /** Cost centres of the fictitious finance ERP; CC-9000 exists but is closed. */
    private static final Map<String, String> COST_CENTRES = Map.ofEntries(
            Map.entry("CC-1000", "University"), Map.entry("CC-1100", "Central Administration"),
            Map.entry("CC-1110", "Human Resources"), Map.entry("CC-1120", "Finance"),
            Map.entry("CC-1130", "IT Services"), Map.entry("CC-2000", "Faculty of Sciences"),
            Map.entry("CC-2100", "Applied Physics"), Map.entry("CC-2200", "Computer Science"),
            Map.entry("CC-2201", "Open Research Data"), Map.entry("CC-2300", "Mathematics"),
            Map.entry("CC-3000", "Faculty of Humanities"), Map.entry("CC-3100", "History"),
            Map.entry("CC-3200", "Linguistics"), Map.entry("CC-9000", "Closed cost centre"));

    private final Outages outages;
    private final List<Map<String, Object>> personnel;
    private final List<Map<String, Object>> orgUnits;
    private final Map<String, String> postings = new ConcurrentHashMap<>();
    private final Map<String, String> travelExports = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(100000);

    MockErpController(Outages outages, ObjectMapper json) throws IOException {
        this.outages = outages;
        this.personnel = read(json, "data/personnel-changes.json");
        this.orgUnits = read(json, "data/org-unit-changes.json");
    }

    // ------------------------------------------------------- personnel (§23.1)

    @GetMapping("/mock/personnel/employees")
    List<Map<String, Object>> employees() {
        outages.check("PERSONNEL_ERP");
        return personnel;
    }

    @GetMapping("/mock/personnel/employees/{personnelNumber}")
    Map<String, Object> employee(@PathVariable String personnelNumber) {
        outages.check("PERSONNEL_ERP");
        return personnel.stream().filter(p -> personnelNumber.equals(p.get("PERS_NR"))).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/mock/personnel/changes")
    List<Map<String, Object>> changes(@RequestParam(defaultValue = "1970-01-01T00:00:00Z") Instant since) {
        outages.check("PERSONNEL_ERP");
        return personnel.stream().filter(p -> Instant.parse((String) p.get("CHANGED_AT")).isAfter(since)).toList();
    }

    @GetMapping("/mock/personnel/organisation-units")
    List<Map<String, Object>> organisationUnits(
            @RequestParam(defaultValue = "1970-01-01T00:00:00Z") Instant since) {
        outages.check("PERSONNEL_ERP");
        return orgUnits.stream().filter(u -> Instant.parse((String) u.get("CHANGED_AT")).isAfter(since)).toList();
    }

    // --------------------------------------------------------- finance (§23.2)

    @GetMapping("/mock/finance/cost-centres/{code}")
    Map<String, Object> costCentre(@PathVariable String code) {
        outages.check("FINANCE_ERP");
        String name = COST_CENTRES.get(code);
        if (name == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return Map.of("KOSTL", code, "KTEXT", name, "ACTIVE", !"CC-9000".equals(code));
    }

    @PostMapping("/mock/finance/postings")
    ResponseEntity<Map<String, String>> post(@RequestHeader("Idempotency-Key") String key,
                                             @RequestBody Map<String, Object> posting) {
        outages.check("FINANCE_ERP");
        boolean repeated = postings.containsKey(key);
        String belnr = postings.computeIfAbsent(key, k -> "FIN-" + sequence.incrementAndGet());
        log.info("Finance posting {} for {} {} ({})", belnr, posting.get("BETRAG"), posting.get("WAERS"),
                repeated ? "repeated request, same document" : "new");
        return ResponseEntity.status(repeated ? HttpStatus.OK : HttpStatus.CREATED).body(Map.of("BELNR", belnr));
    }

    // ---------------------------------------------------------- travel (§23.3)

    @PostMapping("/mock/travel/export")
    Map<String, String> exportTravel(@RequestHeader("Idempotency-Key") String key,
                                     @RequestBody Map<String, Object> travel) {
        outages.check("TRAVEL_ERP");
        return Map.of("REISENR", travelExports.computeIfAbsent(key, k -> "TRV-" + sequence.incrementAndGet()));
    }

    @PostMapping("/mock/travel/settlement")
    Map<String, String> settlement(@RequestHeader("Idempotency-Key") String key,
                                   @RequestBody Map<String, Object> settlement) {
        outages.check("TRAVEL_ERP");
        if (!travelExports.containsValue(String.valueOf(settlement.get("REISENR")))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown travel number");
        }
        return Map.of("REISENR", travelExports.computeIfAbsent(key, k -> "STL-" + sequence.incrementAndGet()));
    }

    // ----------------------------------------------------------- demo control

    @GetMapping("/mock/admin/status")
    Map<String, String> status() {
        return outages.status();
    }

    @PostMapping("/mock/admin/outage/{system}")
    Map<String, String> outage(@PathVariable String system, @RequestParam boolean down) {
        outages.set(system, down);
        log.warn("{} simulated outage: {}", system, down ? "DOWN" : "UP");
        return outages.status();
    }

    private static List<Map<String, Object>> read(ObjectMapper json, String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return json.readValue(in, new TypeReference<>() { });
        }
    }
}
