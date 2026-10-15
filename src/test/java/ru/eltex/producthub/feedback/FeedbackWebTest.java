package ru.eltex.producthub.feedback;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.eltex.producthub.importer.SeedImportService;

@SpringBootTest(properties = "producthub.dev-login.hidden=admin,partner")
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class FeedbackWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    SeedImportService importer;

    @BeforeEach
    void seed() {
        importer.execute("test");
    }

    private static String json(String path, String body) {
        return "{\"pagePath\":\"" + path + "\",\"pageQuery\":\"\",\"pageTitle\":\"Каталог\",\"route\":\"/catalog\",\"view\":\"catalog/index\","
                + "\"selector\":\"main > h1\",\"tag\":\"h1\",\"text\":\"Каталог\",\"kind\":\"BUG\",\"body\":\"" + body + "\"}";
    }

    @Test
    @WithUserDetails("sales")
    void browserCanPostAndReadComments() throws Exception {
        String path = "/catalog-web-" + System.nanoTime();
        mvc.perform(post("/feedback/comments").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(path, "Заголовок мелкий")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Заголовок мелкий"))
                .andExpect(jsonPath("$.onElement").value(true));
        mvc.perform(get("/feedback/comments").param("path", path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].selector").value("main > h1"));
    }

    @Test
    @WithUserDetails("sales")
    void postWithoutCsrfIsRejectedAndEmptyBodyIsBadRequest() throws Exception {
        mvc.perform(post("/feedback/comments").contentType(MediaType.APPLICATION_JSON).content(json("/x", "текст")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/feedback/comments").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json("/x", "  ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.body").value("feedback.error.empty"));
    }

    @Test
    @WithUserDetails("sales")
    void salesCannotOpenManagementPageOrExport() throws Exception {
        mvc.perform(get("/feedback")).andExpect(status().isForbidden());
        mvc.perform(get("/feedback/export")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("product")
    void productSeesManagementPageAndDownloadsFile() throws Exception {
        mvc.perform(get("/feedback")).andExpect(status().isOk()).andExpect(content().string(containsString("Выгрузить файл")));
        mvc.perform(get("/feedback/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("product-hub-comments-")))
                .andExpect(header().string("Content-Disposition", containsString(".md")))
                .andExpect(content().string(containsString("Как читать этот файл")));
    }

    @Test
    @WithUserDetails("sales")
    void everyPageCarriesCommentButtonAndPageContext() throws Exception {
        mvc.perform(get("/catalog"))
                .andExpect(content().string(containsString("id=\"hub-feedback\"")))
                .andExpect(content().string(containsString("data-view=\"catalog/index\"")))
                .andExpect(content().string(containsString("data-route=\"/catalog\"")));
    }

    @Test
    void hiddenAccountsAreNotOnLoginPage() throws Exception {
        mvc.perform(get("/dev/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Тест Продакт-менеджер")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Тест Администратор"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Тест Партнер"))))
                .andExpect(content().string(containsString("Роман Полухин")))
                .andExpect(content().string(containsString("Михаил Моисеев")));
    }
}
