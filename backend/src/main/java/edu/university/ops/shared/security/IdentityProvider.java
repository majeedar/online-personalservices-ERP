package edu.university.ops.shared.security;

import java.util.Optional;

/**
 * Port to the university identity system (AGENT.md §11). The prototype uses
 * {@code MockIdentityProvider}; an LDAP/SAML/OIDC adapter can replace it without
 * touching business modules. This is the only identity port (the §22
 * {@code IdentityGateway} is merged into it).
 */
public interface IdentityProvider {

    /**
     * Verifies credentials against the identity system.
     *
     * @throws InvalidCredentialsException if the username is unknown or the password is wrong
     */
    AuthenticatedUser authenticate(String username, String password);

    Optional<ExternalIdentity> findIdentity(String username);

    record AuthenticatedUser(String username) {
    }

    record ExternalIdentity(String username, String displayName, String email) {
    }

    class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid username or password");
        }
    }
}
