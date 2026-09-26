/**
 * Shared platform: security, audit, error handling, monitoring, configuration,
 * and (in later phases) workflow, notification, documents, batch and reporting.
 *
 * <p>Declared OPEN so business modules may use any of its subpackages. It must never
 * depend on a business module; where it needs module data (e.g. user accounts for
 * login) it defines a port that the module implements.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Shared Platform",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package edu.university.ops.shared;
