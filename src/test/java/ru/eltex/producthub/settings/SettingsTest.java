package ru.eltex.producthub.settings;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.eltex.producthub.importer.SeedImportService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class SettingsTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserSettingsService service;
    @Autowired
    HubUserDetailsService users;
    @Autowired
    SeedImportService importer;

    HubUserDetails sales;
    HubUserDetails product;

    @BeforeEach
    void setUp() {
        importer.execute("test");
        sales = users.loadUserByUsername("sales");
        product = users.loadUserByUsername("product");
        service.save(sales.getId(), UserSettingsView.DEFAULTS);
        service.save(product.getId(), UserSettingsView.DEFAULTS);
    }

    @Test
    void defaultsAreTopMenuNormalDensity() {
        UserSettingsView v = service.get(users.loadUserByUsername("head").getId());
        assertEquals(MenuLayout.TOP, v.menuLayout());
        assertEquals(Density.NORMAL, v.density());
        assertEquals(StartPage.HOME, v.startPage());
        assertFalse(v.sidebarCollapsed());
        assertEquals("layout-top", v.bodyClasses());
    }

    @Test
    void settingsAreSavedPerUser() {
        service.save(sales.getId(), new UserSettingsView(MenuLayout.SIDE, true, Density.COMPACT, StartPage.CATALOG, false, true));
        UserSettingsView mine = service.get(sales.getId());
        assertEquals(MenuLayout.SIDE, mine.menuLayout());
        assertTrue(mine.sidebarCollapsed());
        assertEquals("layout-side sidebar-collapsed density-compact", mine.bodyClasses());
        assertEquals(MenuLayout.TOP, service.get(product.getId()).menuLayout(), "чужие настройки не меняются");
    }

    @Test
    @WithUserDetails("sales")
    void settingsPageOpensForAnyInternalUser() throws Exception {
        mvc.perform(get("/settings")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Меню")))
                .andExpect(content().string(containsString("Плотность таблиц")))
                .andExpect(content().string(containsString("После входа открывать")));
    }

    @Test
    @WithUserDetails("partner")
    void partnerHasNoSettingsPage() throws Exception {
        mvc.perform(get("/settings")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("sales")
    void topMenuByDefaultWithoutSidebar() throws Exception {
        mvc.perform(get("/catalog"))
                .andExpect(content().string(containsString("class=\"layout-top\"")))
                .andExpect(content().string(containsString("class=\"topbar\"")))
                .andExpect(content().string(not(containsString("class=\"sidebar\""))))
                .andExpect(content().string(not(containsString("sidebarToggle"))));
    }

    @Test
    @WithUserDetails("sales")
    void savingSideLayoutSwitchesPagesToSidebarWithToggle() throws Exception {
        mvc.perform(post("/settings").with(csrf()).param("menuLayout", "SIDE").param("density", "COMPACT")
                        .param("startPage", "CATALOG"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/catalog"))
                .andExpect(content().string(containsString("layout-side density-compact")))
                .andExpect(content().string(containsString("class=\"sidebar\"")))
                .andExpect(content().string(containsString("sidebarToggle")))
                .andExpect(content().string(not(containsString("class=\"topbar\""))))
                .andExpect(content().string(containsString("Настройки")));
        // меню показывается один раз, а не дублируется определением фрагмента
        String html = mvc.perform(get("/catalog")).andReturn().getResponse().getContentAsString();
        assertEquals(1, html.split("class=\"menu\"", -1).length - 1, "меню на странице одно");
    }

    @Test
    @WithUserDetails("sales")
    void collapsingSidebarIsRemembered() throws Exception {
        service.save(sales.getId(), new UserSettingsView(MenuLayout.SIDE, false, Density.NORMAL, StartPage.HOME, false, false));
        mvc.perform(post("/settings/sidebar").with(csrf()).param("collapsed", "true")).andExpect(status().isNoContent());
        mvc.perform(get("/catalog")).andExpect(content().string(containsString("layout-side sidebar-collapsed")));
        mvc.perform(post("/settings/sidebar").with(csrf()).param("collapsed", "false")).andExpect(status().isNoContent());
        mvc.perform(get("/catalog")).andExpect(content().string(not(containsString("sidebar-collapsed\""))));
    }

    @Test
    @WithUserDetails("sales")
    void menuItemsStillFollowRolesInSidebar() throws Exception {
        service.save(sales.getId(), new UserSettingsView(MenuLayout.SIDE, false, Density.NORMAL, StartPage.HOME, false, false));
        mvc.perform(get("/catalog"))
                .andExpect(content().string(containsString("Каталог")))
                .andExpect(content().string(not(containsString("Справочники"))))
                .andExpect(content().string(not(containsString("Дашборд"))));
    }

    @Test
    void startPageIsOpenedAfterSwitchingUser() throws Exception {
        service.save(sales.getId(), new UserSettingsView(MenuLayout.TOP, false, Density.NORMAL, StartPage.CATALOG, false, false));
        mvc.perform(post("/dev/login-as").param("login", "sales").with(csrf()).session(new MockHttpSession()))
                .andExpect(redirectedUrl("/catalog"));
        mvc.perform(post("/dev/login-as").param("login", "head").with(csrf()).session(new MockHttpSession()))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @WithUserDetails("product")
    void catalogDefaultsFollowPersonalSettings() throws Exception {
        // без настроек: все SKU, EOL скрыты
        mvc.perform(get("/catalog")).andExpect(content().string(not(containsString("Найдено: 0"))));
        service.save(product.getId(), new UserSettingsView(MenuLayout.TOP, false, Density.NORMAL, StartPage.HOME, true, true));
        // только мои + EOL: владелец по умолчанию - тест-продакт, у него SKU нет
        mvc.perform(get("/catalog")).andExpect(content().string(containsString("Найдено: 0")));
        // явные параметры в адресе важнее настроек
        mvc.perform(get("/catalog?owner=")).andExpect(content().string(not(containsString("Найдено: 0"))));
    }
}
