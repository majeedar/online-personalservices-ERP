package edu.university.ops;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enforces the architecture decisions in docs/architecture.md so they cannot
 * silently erode.
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("edu.university.ops");

    /** ADR-001/003: module boundaries, no cycles, only public module APIs are used across modules. */
    @Test
    void moduleStructureIsValid() {
        ApplicationModules.of(OnlinePersonalservicesApplication.class).verify();
    }

    /** ADR-002: dependencies point inward; the domain knows no adapters or use-case code. */
    @Test
    void domainDoesNotDependOnOuterLayers() {
        noClasses().that().resideInAPackage("edu.university.ops..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("edu.university.ops..api..",
                        "edu.university.ops..application..", "edu.university.ops..persistence..",
                        "edu.university.ops..integration..")
                .check(CLASSES);
    }

    @Test
    void apiDoesNotUsePersistenceDirectly() {
        noClasses().that().resideInAPackage("edu.university.ops..api..")
                .should().dependOnClassesThat().resideInAPackage("edu.university.ops..persistence..")
                .check(CLASSES);
    }

    /** AGENT.md §76: never return JPA entities through REST. */
    @Test
    void controllersDoNotReturnEntities() {
        methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .should(notReturnAnEntity())
                .check(CLASSES);
    }

    /** ADR-008: audit records are append-only at the application level too. */
    @Test
    void auditRepositoryOffersNoDeletion() {
        methods().that().areDeclaredIn(edu.university.ops.shared.audit.AuditLogRepository.class)
                .should().haveNameNotMatching("(delete|remove).*")
                .check(CLASSES);
    }

    private static ArchCondition<JavaMethod> notReturnAnEntity() {
        return new ArchCondition<>("not return a JPA entity") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean entity = method.getRawReturnType().isAnnotatedWith(Entity.class);
                events.add(new SimpleConditionEvent(method, !entity,
                        method.getFullName() + " returns entity " + method.getRawReturnType().getName()));
            }
        };
    }
}
