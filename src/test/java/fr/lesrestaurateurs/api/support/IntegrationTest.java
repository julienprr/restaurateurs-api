package fr.lesrestaurateurs.api.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base des tests qui ont besoin de l'application entière et d'une vraie base.
 *
 * <p>PostgreSQL tourne dans Docker, migré par Flyway comme en production : la
 * règle « une seule sortie planifiée par groupe » vit dans un index partiel,
 * qu'aucune base embarquée ne reproduirait fidèlement.
 *
 * <p>Le conteneur est démarré une fois pour toute la suite et jamais arrêté à
 * la main : Testcontainers le supprime à la fin. Les tests ne nettoient pas la
 * base, chacun crée ses propres utilisateurs et groupes.
 */
@SpringBootTest(properties = "app.admin-token=" + IntegrationTest.ADMIN_TOKEN)
@AutoConfigureMockMvc
@Import(Fixtures.class)
public abstract class IntegrationTest {

    protected static final String ADMIN_TOKEN = "jeton-de-test";

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected Fixtures fixtures;
}
