package edu.university.ops.shared.security;

import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Static access to the current principal for application services. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<OpsPrincipal> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OpsPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static OpsPrincipal require() {
        return find().orElseThrow(() -> new BusinessException(ErrorCode.NOT_AUTHENTICATED, "Please log in."));
    }
}
