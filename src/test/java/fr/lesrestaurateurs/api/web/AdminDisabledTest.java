package fr.lesrestaurateurs.api.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lesrestaurateurs.api.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Sans jeton configuré, les routes d'administration n'existent pas. */
@TestPropertySource(properties = "app.admin-token=")
class AdminDisabledTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("404, même avec un jeton vide envoyé")
    void notFound() throws Exception {
        mvc.perform(post("/api/admin/outings/close")).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/outings/close").header("X-Admin-Token", ""))
                .andExpect(status().isNotFound());
    }
}
