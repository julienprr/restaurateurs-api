package fr.lesrestaurateurs.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Réglages applicatifs (préfixe {@code app} dans application.yml).
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl,
        String mailFrom,
        int magicLinkTtlMinutes,
        int sessionTtlDays,
        boolean logMagicLink,
        boolean cookieSecure
) {
}
