package ru.eltex.producthub.dashboard;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.eltex.producthub.dashboard.service.ViewLogService;
import ru.eltex.producthub.importer.SeedImportService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class DashboardWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    SeedImportService importer;
    @Autowired
    ViewLogService viewLog;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        importer.execute("test");
    }

    @Test
    @WithUserDetails("head")
    void headSeesDashboardWithClickableNumbers() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Покрытие")))
                .andExpect(content().string(containsString("/catalog?status=")))
                .andExpect(content().string(containsString("/artifacts?reviewDue=true")))
                .andExpect(content().string(containsString("Все продакты")));
    }

    @Test
    @WithUserDetails("product")
    void productSeesOwnDashboardWithoutPicker() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("по вашим SKU")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Все продакты"))));
    }

    @Test
    @WithUserDetails("sales")
    void salesCannotOpenDashboard() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("sales")
    void openingSkuAndArtifactWritesViewLog() throws Exception {
        long before = jdbc.queryForObject("select count(*) from view_log where user_login = 'sales'", Long.class);
        mvc.perform(get("/sku/WEP-550K")).andExpect(status().isOk());
        viewLog.drain();
        org.junit.jupiter.api.Assertions.assertEquals(before + 1,
                jdbc.queryForObject("select count(*) from view_log where user_login = 'sales'", Long.class));
    }
}
