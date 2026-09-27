package edu.university.ops.shared.i18n;

import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * The SPA sends its interface language as Accept-Language (ADR-020). Without the
 * header, or for an unsupported language, texts are English, whatever the server's
 * default locale is.
 */
@Configuration(proxyBeanMethods = false)
class LocaleConfiguration {

    @Bean
    LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(Translator.SUPPORTED);
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }
}
