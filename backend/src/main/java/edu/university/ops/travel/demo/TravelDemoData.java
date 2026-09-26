package edu.university.ops.travel.demo;

import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.security.RunAs;
import edu.university.ops.shared.workflow.WorkflowEnums.Decision;
import edu.university.ops.travel.application.TravelService;
import edu.university.ops.travel.domain.TravelPorts.TravelRequestRepository;
import edu.university.ops.travel.domain.TravelRequest;
import edu.university.ops.travel.domain.TravelRequest.TransportMode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * DEMO DATA ONLY (ADR-009). Trips in each interesting state (AGENT.md §56),
 * created through the real services: two authorized (one already travelled, for
 * Demo Scenario 4), one awaiting the supervisor, one awaiting financial approval.
 */
@Component
@Profile("demo")
@Order(30)
class TravelDemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TravelDemoData.class);
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final TravelRequestRepository requests;
    private final TravelService travel;
    private final RunAs runAs;
    private final Clock clock;

    TravelDemoData(TravelRequestRepository requests, TravelService travel, RunAs runAs, Clock clock) {
        this.requests = requests;
        this.travel = travel;
        this.runAs = runAs;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (requests.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        seed("employee", "Workshop on research data management", "Hamburg", "DE", today.minusDays(15), 2,
                TransportMode.TRAIN, "420.00", "CC-2200", List.of("supervisor", "finance"));
        seed("eprobe", "Conference: Applied Physics Days (talk)", "Vienna", "AT", today.plusDays(24), 3,
                TransportMode.TRAIN, "890.00", "CC-2100", List.of("supervisor", "finance"));
        seed("parttime", "Project meeting with partner institute", "Leipzig", "DE", today.plusDays(17), 1,
                TransportMode.TRAIN, "210.00", "CC-2200", List.of());
        seed("cbeispiel", "Summer school lecture", "Utrecht", "NL", today.plusDays(38), 4,
                TransportMode.FLIGHT, "1150.00", "CC-2201", List.of("supervisor"));
        log.info("Demo travel requests created");
    }

    private void seed(String username, String purpose, String city, String country, LocalDate startDate, int days,
                      TransportMode mode, String cost, String costCentre, List<String> approvers) {
        try {
            OpsPrincipal traveller = runAs.principal(username);
            var details = new TravelRequest.Details(purpose, city, country,
                    ZonedDateTime.of(startDate, LocalTime.of(7, 30), ZONE).toInstant(),
                    ZonedDateTime.of(startDate.plusDays(days - 1L), LocalTime.of(20, 0), ZONE).toInstant(), mode,
                    new BigDecimal(cost), "EUR", costCentre, null, null);
            TravelRequest request = runAs.call(traveller, () -> {
                TravelRequest draft = travel.createDraft(traveller, details, List.of());
                return travel.submit(draft.getId(), traveller);
            });
            for (String approver : approvers) {
                OpsPrincipal decider = runAs.principal(approver);
                runAs.call(decider, () -> travel.decide(request.getId(), Decision.APPROVE, null, null, decider));
            }
        } catch (RuntimeException e) {
            log.warn("Demo trip for {} skipped: {}", username, e.getMessage());
        }
    }
}
