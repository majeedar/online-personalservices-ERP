package edu.university.ops.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.shared.exception.ErrorResponse;
import edu.university.ops.shared.monitoring.CorrelationId;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * Session-cookie authentication with CSRF protection (ADR-005). The Angular app
 * is served from the same origin via nginx, so no CORS configuration is needed.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ObjectMapper objectMapper,
                                    CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/session").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Scraped by Prometheus on the internal network only; nginx never proxies /actuator.
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/**").hasAnyRole(Role.ERP_ADMIN.name(), Role.SUPPORT.name())
                        .requestMatchers("/api/v1/admin/**").hasAnyRole(Role.ERP_ADMIN.name(), Role.SUPPORT.name(),
                                Role.AUDITOR.name())
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository()))
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                write(res, objectMapper, ErrorCode.NOT_AUTHENTICATED, "Please log in."))
                        .accessDeniedHandler((req, res, e) -> {
                            if (e instanceof CsrfException) {
                                write(res, objectMapper, ErrorCode.CSRF_TOKEN_INVALID,
                                        "Your session token is missing or expired. Reload the page.");
                            } else {
                                write(res, objectMapper, ErrorCode.NOT_AUTHORIZED,
                                        "You are not authorized to perform this action.");
                            }
                        }));
        return http.build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(c -> c.path("/").sameSite("Lax"));
        return repository;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    AuthenticationManager authenticationManager(OpsAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void write(HttpServletResponse res, ObjectMapper mapper, ErrorCode code, String message)
            throws IOException {
        res.setStatus(code.status().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), ErrorResponse.of(code, message, CorrelationId.current()));
    }
}
