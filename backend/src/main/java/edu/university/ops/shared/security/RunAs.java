package edu.university.ops.shared.security;

import java.util.List;
import java.util.function.Supplier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Executes code as a given employee outside an HTTP request (demo seeding,
 * batch jobs acting for a user). Audit records then name the right actor.
 */
@Component
public class RunAs {

    private final UserAccountLookup accounts;

    RunAs(UserAccountLookup accounts) {
        this.accounts = accounts;
    }

    public OpsPrincipal principal(String username) {
        var account = accounts.findActiveAccount(username)
                .orElseThrow(() -> new IllegalArgumentException("No active account " + username));
        return new OpsPrincipal(account.employeeId(), account.username(), account.displayName(), account.roles());
    }

    public <T> T call(OpsPrincipal principal, Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                .map(r -> new SimpleGrantedAuthority(r.authority())).toList();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
