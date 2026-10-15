package ru.eltex.producthub.sku;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class CatalogWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    SeedImportService importer;
    @Autowired
    ru.eltex.producthub.sku.service.SkuService skuService;

    @BeforeEach
    void seed() {
        importer.execute("test");
    }

    @Test
    @WithUserDetails("sales")
    void catalogListsSkusAndHidesEolByDefault() throws Exception {
        mvc.perform(get("/catalog?q=wep550k"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("WEP-550K")));
        // ESR-1511 rev.B снят с производства (EOL): скрыт, пока не включена галочка
        mvc.perform(get("/catalog?q=ESR-1511"))
                .andExpect(content().string(not(containsString("ESR-1511 rev.B"))));
        mvc.perform(get("/catalog?q=ESR-1511&includeEol=true"))
                .andExpect(content().string(containsString("ESR-1511 rev.B")));
    }

    @Test
    @WithUserDetails("sales")
    void filtersByLineAndStatus() throws Exception {
        mvc.perform(get("/catalog?line=WLC"))
                .andExpect(content().string(containsString("WLC-3200")))
                .andExpect(content().string(not(containsString("WEP-550K"))));
        mvc.perform(get("/catalog?line=WEP&status=NEW"))
                .andExpect(content().string(containsString("WEP-550K")));
    }

    @Test
    @WithUserDetails("sales")
    void filterListsShowLabelAsHiddenPlaceholderAndLinesKnowTheirFamily() throws Exception {
        mvc.perform(get("/catalog"))
                .andExpect(content().string(containsString("<option value=\"\" hidden>Семейство</option>")))
                .andExpect(content().string(containsString("<option value=\"\" hidden>Линейка</option>")))
                .andExpect(content().string(containsString("data-family=\"Wi-Fi\"")))
                .andExpect(content().string(containsString("data-family=\"PON\"")))
                .andExpect(content().string(not(containsString(">Все</option>"))));
    }

    @Test
    @WithUserDetails("sales")
    void htmxRequestReturnsOnlyResultsFragment() throws Exception {
        mvc.perform(get("/catalog?line=WLC").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("WLC-3200")))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    @WithUserDetails("sales")
    void cardOpensByPrettyUrlIncludingModelWithSlash() throws Exception {
        mvc.perform(get("/sku/WEP-550K"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("WEP-550K")))
                .andExpect(content().string(containsString("Mediatek")));
        mvc.perform(get("/sku/wep550k")).andExpect(status().isOk());
        mvc.perform(get("/sku/PM300T-220/12")).andExpect(status().isOk())
                .andExpect(content().string(containsString("PM300T-220/12")));
        mvc.perform(get("/sku/NO-SUCH-SKU")).andExpect(status().isNotFound());
    }

    @Test
    @WithUserDetails("sales")
    void salesDoesNotSeeEditButtonsOrCreate() throws Exception {
        mvc.perform(get("/sku/WEP-550K"))
                .andExpect(content().string(not(containsString("/edit/main"))));
        mvc.perform(get("/catalog")).andExpect(content().string(not(containsString("/skus/new"))));
        mvc.perform(get("/skus/new")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("product")
    void productEditsBlockThroughHtmx() throws Exception {
        mvc.perform(get("/sku/WEP-550K")).andExpect(content().string(containsString("/edit/main")));
        mvc.perform(post("/skus/999999/edit/text").with(csrf()).param("description", "x"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithUserDetails("product")
    void savingBlockAlsoRefreshesHistoryAndCompleteness() throws Exception {
        Long id = skuService.findIdByModel("WLC-3200");
        mvc.perform(post("/skus/" + id + "/edit/text").with(csrf()).param("description", "Контроллер для проверки"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Контроллер для проверки")))
                .andExpect(content().string(containsString("hx-swap-oob=\"true\"")))
                .andExpect(content().string(containsString("block-history")));
    }

    @Test
    @WithUserDetails("product")
    void validationErrorReturnsFormWithMessage() throws Exception {
        Long id = skuService.findIdByModel("WLC-3200");
        mvc.perform(post("/skus/" + id + "/edit/lifecycle").with(csrf())
                        .param("lifecycleStatus", "PRODUCTION").param("gaDate", "скоро"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Нужен квартал")));
    }

    @Test
    @WithUserDetails("sales")
    void salesCannotPostEdits() throws Exception {
        mvc.perform(post("/skus/1/edit/text").with(csrf()).param("description", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("admin")
    void importPageAndRunAreAdminOnly() throws Exception {
        mvc.perform(get("/directories")).andExpect(status().isOk());
        mvc.perform(post("/directories/import").with(csrf())).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithUserDetails("product")
    void importRunIsForbiddenForProduct() throws Exception {
        mvc.perform(post("/directories/import").with(csrf())).andExpect(status().isForbidden());
    }
}
