package fr.lesrestaurateurs.api.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.domain.UserGroup;
import fr.lesrestaurateurs.api.security.AuthFilter;
import fr.lesrestaurateurs.api.service.OutingService;
import fr.lesrestaurateurs.api.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Les routes des sorties, de bout en bout : cookie, sérialisation, codes HTTP.
 * Les règles métier elles-mêmes sont couvertes par OutingServiceTest.
 */
class OutingApiTest extends IntegrationTest {

    private static final LocalDate NEXT_WEEK = LocalDate.now(OutingService.ZONE).plusDays(7);

    @Autowired
    MockMvc mvc;

    User alice;
    UserGroup group;
    Cookie session;

    @BeforeEach
    void setUp() {
        alice = fixtures.user("Alice");
        group = fixtures.group(alice);
        fixtures.restaurant(group, "Sushi", alice);
        session = new Cookie(AuthFilter.COOKIE_NAME, fixtures.sessionCookie(alice));
    }

    @Test
    @DisplayName("sans connexion : 401")
    void anonymousIsRejected() throws Exception {
        mvc.perform(post("/api/groups/{id}/outings", group.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dateBody(NEXT_WEEK)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("prévoir, consulter, décider, annuler")
    void fullCycle() throws Exception {
        mvc.perform(get("/api/groups/{id}/outings/planned", group.getId()).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outing").value(nullValue()));

        String created = mvc.perform(post("/api/groups/{id}/outings", group.getId())
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dateBody(NEXT_WEEK)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANIFIEE"))
                .andExpect(jsonPath("$.plannedFor").value(NEXT_WEEK.toString()))
                .andReturn().getResponse().getContentAsString();
        String outingId = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/groups/{id}/outings/planned", group.getId()).cookie(session))
                .andExpect(jsonPath("$.outing.id").value(outingId));

        mvc.perform(patch("/api/outings/{id}", outingId)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dateBody(NEXT_WEEK.plusDays(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedFor").value(NEXT_WEEK.plusDays(1).toString()));

        mvc.perform(post("/api/outings/{id}/decide", outingId).param("mode", "votes").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outingId").value(outingId))
                .andExpect(jsonPath("$.winner.name").value("Sushi"));

        mvc.perform(post("/api/outings/{id}/cancel", outingId).cookie(session))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/groups/{id}/outings/planned", group.getId()).cookie(session))
                .andExpect(jsonPath("$.outing").value(nullValue()));
    }

    @Test
    @DisplayName("erreurs métier : 400 avec un message affichable")
    void businessErrors() throws Exception {
        mvc.perform(post("/api/groups/{id}/outings", group.getId())
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choisis une date pour la sortie."));

        String created = mvc.perform(post("/api/groups/{id}/outings", group.getId())
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dateBody(NEXT_WEEK)))
                .andReturn().getResponse().getContentAsString();
        String outingId = JsonPath.read(created, "$.id");

        mvc.perform(post("/api/groups/{id}/outings", group.getId())
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dateBody(NEXT_WEEK)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString());

        mvc.perform(post("/api/outings/{id}/decide", outingId).param("mode", "pile-ou-face").cookie(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("choisir ce soir : 409 si une sortie est prévue un autre jour, sinon le verdict")
    void tonight() throws Exception {
        String created = mvc.perform(post("/api/groups/{id}/outings", group.getId())
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dateBody(NEXT_WEEK)))
                .andReturn().getResponse().getContentAsString();
        String outingId = JsonPath.read(created, "$.id");

        mvc.perform(post("/api/groups/{id}/outings/tonight", group.getId()).cookie(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Une sortie est déjà prévue le ")));

        mvc.perform(post("/api/outings/{id}/cancel", outingId).cookie(session))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/groups/{id}/outings/tonight", group.getId()).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winner.name").value("Sushi"))
                .andExpect(jsonPath("$.mode").value("hasard"));
    }

    @Test
    @DisplayName("clôture à la main : jeton exigé, date du jour simulable")
    void adminClosure() throws Exception {
        String created = mvc.perform(post("/api/groups/{id}/outings", group.getId())
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dateBody(NEXT_WEEK)))
                .andReturn().getResponse().getContentAsString();
        String outingId = JsonPath.read(created, "$.id");
        mvc.perform(post("/api/outings/{id}/decide", outingId).cookie(session))
                .andExpect(status().isOk());

        mvc.perform(post("/api/admin/outings/close"))
                .andExpect(status().isForbidden());
        // Une session de membre ne suffit pas.
        mvc.perform(post("/api/admin/outings/close").cookie(session).header("X-Admin-Token", "faux"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/outings/close")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .param("today", NEXT_WEEK.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(outingId)));

        mvc.perform(get("/api/groups/{id}/outings", group.getId()).cookie(session))
                .andExpect(jsonPath("$[0].outing.id").value(outingId))
                .andExpect(jsonPath("$[0].outing.status").value("PASSEE"))
                .andExpect(jsonPath("$[0].outing.restaurant.status").value("DEJA_FAIT"));
    }

    @Test
    @DisplayName("noter : 204, puis la note dans l'historique ; note hors bornes : 400")
    void rating() throws Exception {
        String created = mvc.perform(post("/api/groups/{id}/outings", group.getId())
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dateBody(NEXT_WEEK)))
                .andReturn().getResponse().getContentAsString();
        String outingId = JsonPath.read(created, "$.id");
        mvc.perform(post("/api/outings/{id}/decide", outingId).cookie(session));
        mvc.perform(post("/api/admin/outings/close")
                .header("X-Admin-Token", ADMIN_TOKEN)
                .param("today", NEXT_WEEK.plusDays(1).toString()));

        mvc.perform(put("/api/outings/{id}/rating", outingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":4}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/outings/{id}/rating", outingId)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":6}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La note va de 1 à 5."));
        mvc.perform(put("/api/outings/{id}/rating", outingId)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Sans note\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choisis une note."));

        mvc.perform(put("/api/outings/{id}/rating", outingId)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":4,\"comment\":\"On y retourne\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/groups/{id}/outings", group.getId()).cookie(session))
                .andExpect(jsonPath("$[0].average").value(4.0))
                .andExpect(jsonPath("$[0].ratings[0].author").value("Alice"))
                .andExpect(jsonPath("$[0].ratings[0].score").value(4))
                .andExpect(jsonPath("$[0].ratings[0].comment").value("On y retourne"))
                .andExpect(jsonPath("$[0].ratings[0].mine").value(true));
    }

    private static String dateBody(LocalDate date) {
        return "{\"plannedFor\":\"" + date + "\"}";
    }
}
