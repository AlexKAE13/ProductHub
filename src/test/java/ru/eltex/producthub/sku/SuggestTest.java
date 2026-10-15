package ru.eltex.producthub.sku;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.eltex.producthub.importer.SeedImportService;

/** Подсказки артикулов и линеек в полях форм. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class SuggestTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    SeedImportService importer;

    @BeforeEach
    void setUp() {
        importer.execute("test");
    }

    @Test
    @WithUserDetails("sales")
    void skuSuggestMatchesPrefixAndIgnoresPunctuation() throws Exception {
        mvc.perform(get("/suggest/skus").param("q", "wep55")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value("WEP-550K"));
        mvc.perform(get("/suggest/skus").param("q", "WEP")).andExpect(jsonPath("$", hasSize(lessThanOrEqualTo(8))));
        mvc.perform(get("/suggest/skus").param("q", "")).andExpect(content().json("[]"));
        mvc.perform(get("/suggest/skus").param("q", "нет-такого-артикула-zzz")).andExpect(content().json("[]"));
    }

    @Test
    @WithUserDetails("sales")
    void lineSuggestFindsByCode() throws Exception {
        mvc.perform(get("/suggest/lines").param("q", "we")).andExpect(status().isOk())
                .andExpect(content().string(containsString("\"value\":\"WEP\"")));
        mvc.perform(get("/suggest/lines").param("q", "zzzz")).andExpect(content().json("[]"));
    }

    @Test
    @WithUserDetails("partner")
    void externalUserGetsNoSuggestions() throws Exception {
        mvc.perform(get("/suggest/skus").param("q", "wep")).andExpect(status().isForbidden());
        mvc.perform(get("/suggest/lines").param("q", "wep")).andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("WEP"))));
    }
}
