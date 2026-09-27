package edu.university.ops.shared.security;

import edu.university.ops.shared.audit.AuditService;
import edu.university.ops.shared.directory.PersonDirectory;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Demo login against the {@link IdentityProvider} (simulated university SSO). */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final AuditService audit;
    private final PersonDirectory persons;

    AuthController(AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository,
                   CsrfTokenRepository csrfTokenRepository, AuditService audit,
                   PersonDirectory persons) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.audit = audit;
        this.persons = persons;
    }

    record LoginRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 128) String password) {
    }

    /** @param language the employee's saved language, null if not chosen yet (the browser's choice applies) */
    record SessionResponse(UUID employeeId, String username, String displayName, Set<Role> roles,
                           String language) {
    }

    private SessionResponse sessionOf(OpsPrincipal p) {
        String language = persons.findPerson(p.employeeId()).map(PersonDirectory.Person::language).orElse(null);
        return new SessionResponse(p.employeeId(), p.username(), p.displayName(), p.roles(), language);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with demo credentials; establishes a session cookie")
    SessionResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                          HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        } catch (AuthenticationException e) {
            audit.recordAnonymous(body.username(), "LOGIN_FAILED", "Session", null);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password.");
        }

        // Session fixation protection: never reuse a pre-login session ID.
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        // Rotate the CSRF token; the next request receives a fresh XSRF-TOKEN cookie.
        csrfTokenRepository.saveToken(null, request, response);

        OpsPrincipal principal = (OpsPrincipal) authentication.getPrincipal();
        audit.record("LOGIN", "Session", principal.employeeId(), null, Map.of("roles", principal.roles()));
        return sessionOf(principal);
    }

    @GetMapping("/session")
    @Operation(summary = "Current session; 401 if not logged in")
    SessionResponse session() {
        return sessionOf(CurrentUser.require());
    }

    @PostMapping("/logout")
    @Operation(summary = "End the current session")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        CurrentUser.find().ifPresent(p -> audit.record("LOGOUT", "Session", p.employeeId(), null, null));
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        csrfTokenRepository.saveToken(null, request, response);
        return ResponseEntity.noContent().build();
    }
}
