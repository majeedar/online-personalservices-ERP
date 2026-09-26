package edu.university.ops.shared.integration.identity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.ops.shared.security.IdentityProvider;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Simulated university SSO for the prototype (AGENT.md §11, §23.4). Identities
 * and BCrypt password hashes come from {@code mock-identity/identities.json},
 * standing in for an external directory. Active only when
 * {@code ops.identity.provider=mock}.
 */
@Component
@ConditionalOnProperty(name = "ops.identity.provider", havingValue = "mock")
class MockIdentityProvider implements IdentityProvider {

    /** Compared against when the username is unknown, so timing does not reveal valid usernames. */
    private static final String DUMMY_HASH = "$2a$10$pxtyUY4rdW876YZRVdDrA.ZtEgi2uadL1QTQKSdI7THk/RUGuItpa";

    private final PasswordEncoder passwordEncoder;
    private final Map<String, MockIdentity> identities;

    MockIdentityProvider(PasswordEncoder passwordEncoder, ObjectMapper objectMapper) throws IOException {
        this.passwordEncoder = passwordEncoder;
        try (InputStream in = new ClassPathResource("mock-identity/identities.json").getInputStream()) {
            List<MockIdentity> list = objectMapper.readValue(in, new TypeReference<>() { });
            this.identities = list.stream().collect(Collectors.toUnmodifiableMap(MockIdentity::username,
                    Function.identity()));
        }
    }

    record MockIdentity(String username, String passwordHash, String displayName, String email) {
    }

    @Override
    public AuthenticatedUser authenticate(String username, String password) {
        MockIdentity identity = identities.get(username);
        String hash = identity != null ? identity.passwordHash() : DUMMY_HASH;
        boolean matches = passwordEncoder.matches(password, hash);
        if (identity == null || !matches) {
            throw new InvalidCredentialsException();
        }
        return new AuthenticatedUser(identity.username());
    }

    @Override
    public Optional<ExternalIdentity> findIdentity(String username) {
        return Optional.ofNullable(identities.get(username))
                .map(i -> new ExternalIdentity(i.username(), i.displayName(), i.email()));
    }
}
