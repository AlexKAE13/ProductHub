package ru.eltex.producthub.search;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class SearchWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    SeedImportService importer;

    @BeforeEach
    void seed() {
        importer.execute("test");
    }

    @Test
    @WithUserDetails("sales")
    void suggestReturnsGroupedResultsWithLinks() throws Exception {
        mvc.perform(get("/search/suggest").param("q", "wep550k"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("WEP-550K")))
                .andExpect(content().string(containsString("/search/go?to=")));
    }

    @Test
    @WithUserDetails("sales")
    void suggestIgnoresOneCharacterQueries() throws Exception {
        mvc.perform(get("/search/suggest").param("q", "w"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("WEP-550K"))));
    }

    @Test
    @WithUserDetails("sales")
    void resultsPageShowsGroupsAndPagesOneType() throws Exception {
        mvc.perform(get("/search").param("q", "wep"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("WEP-550K")));
        mvc.perform(get("/search").param("q", "wep").param("type", "SKU"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("WEP-550K")));
        mvc.perform(get("/search").param("q", "wep").param("type", "<script>"))
                .andExpect(status().isOk());
    }

    @Test
    @WithUserDetails("sales")
    void clickRedirectsOnlyInsideTheApp() throws Exception {
        mvc.perform(get("/search/go").param("to", "/sku/WEP-550K").param("q", "wep"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/sku/WEP-550K"));
        mvc.perform(get("/search/go").param("to", "https://evil.example/").param("q", "wep"))
                .andExpect(redirectedUrl("/"));
        mvc.perform(get("/search/go").param("to", "//evil.example/").param("q", "wep"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @WithUserDetails("sales")
    void headerHasSearchBox() throws Exception {
        mvc.perform(get("/catalog"))
                .andExpect(content().string(containsString("hx-get=\"/search/suggest\"")));
    }

    @Test
    @WithUserDetails("sales")
    void reindexIsForAdminOnly() throws Exception {
        mvc.perform(post("/directories/reindex").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("admin")
    void adminCanReindex() throws Exception {
        mvc.perform(post("/directories/reindex").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/directories"));
    }
}
