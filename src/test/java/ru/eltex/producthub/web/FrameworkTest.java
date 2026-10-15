package ru.eltex.producthub.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.eltex.producthub.security.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class FrameworkTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Test
    void seedUsersCoverAllRoles() {
        org.junit.jupiter.api.Assertions.assertEquals(16, users.count()); // 7 тестовых, 2 владельца SKU, 7 коллег (миграция V13)
    }

    @Test
    void anonymousIsSentToDevLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dev/login"));
    }

    @Test
    void devLoginPageIsOpenAndListsUsers() throws Exception {
        mvc.perform(get("/dev/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Тест Продакт-менеджер")));
    }

    @Test
    @WithUserDetails("sales")
    void salesSeesBasicMenuOnly() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Каталог")))
                .andExpect(content().string(containsString("Задачи")))
                .andExpect(content().string(not(containsString("Дашборд"))))
                .andExpect(content().string(not(containsString("Справочники"))));
    }

    @Test
    @WithUserDetails("head")
    void headSeesDashboardButNotDirectories() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("Дашборд")))
                .andExpect(content().string(not(containsString("Справочники"))));
        mvc.perform(get("/dashboard")).andExpect(status().isOk());
        mvc.perform(get("/directories")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("product")
    void productSeesDirectoriesAndDashboard() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("Дашборд")))
                .andExpect(content().string(containsString("Справочники")));
        mvc.perform(get("/directories")).andExpect(status().isOk());
    }

    @Test
    @WithUserDetails("admin")
    void adminSeesEverything() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("Дашборд")))
                .andExpect(content().string(containsString("Справочники")));
        mvc.perform(get("/dashboard")).andExpect(status().isOk());
        mvc.perform(get("/directories")).andExpect(status().isOk());
    }

    @Test
    @WithUserDetails("sales")
    void salesCannotOpenRestrictedSections() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().isForbidden());
        mvc.perform(get("/directories")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("partner")
    void partnerHasNoAccessToInternalPages() throws Exception {
        mvc.perform(get("/")).andExpect(status().isForbidden());
        mvc.perform(get("/catalog")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("product")
    void responseHasServerTimingHeaderAndFooterTime() throws Exception {
        mvc.perform(get("/catalog"))
                .andExpect(status().isOk())
                .andExpect(header().string("Server-Timing", containsString("app;dur=")))
                .andExpect(content().string(containsString("Время ответа:")));
    }

    @Test
    void switchingUserChangesAccess() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/dev/login-as").param("login", "sales").with(csrf()).session(session))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/directories").session(session)).andExpect(status().isForbidden());

        mvc.perform(post("/dev/login-as").param("login", "admin").with(csrf()).session(session))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/directories").session(session)).andExpect(status().isOk());
    }

    @Test
    void loginAsRequiresCsrfToken() throws Exception {
        mvc.perform(post("/dev/login-as").param("login", "admin")).andExpect(status().isForbidden());
    }
}
