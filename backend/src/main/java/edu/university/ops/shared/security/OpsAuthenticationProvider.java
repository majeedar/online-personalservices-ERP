package edu.university.ops.shared.security;

import java.util.List;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Two-step login: the identity system verifies credentials, then the employee
 * directory decides whether the person may use this application and with which roles.
 */
@Component
class OpsAuthenticationProvider implements AuthenticationProvider {

    private final IdentityProvider identityProvider;
    private final UserAccountLookup accounts;

    OpsAuthenticationProvider(IdentityProvider identityProvider, UserAccountLookup accounts) {
        this.identityProvider = identityProvider;
        this.accounts = accounts;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String username = authentication.getName();
        String password = String.valueOf(authentication.getCredentials());
        try {
            identityProvider.authenticate(username, password);
        } catch (IdentityProvider.InvalidCredentialsException e) {
            throw new BadCredentialsException("Invalid username or password");
        }
        var account = accounts.findActiveAccount(username)
                .orElseThrow(() -> new DisabledException("No active employee record for this identity"));

        var principal = new OpsPrincipal(account.employeeId(), account.username(), account.displayName(),
                account.roles());
        List<SimpleGrantedAuthority> authorities = account.roles().stream()
                .map(r -> new SimpleGrantedAuthority(r.authority()))
                .toList();
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
