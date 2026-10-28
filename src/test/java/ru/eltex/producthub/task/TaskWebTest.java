package ru.eltex.producthub.task;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.eltex.producthub.importer.SeedImportService;
import ru.eltex.producthub.qa.domain.QuestionKind;
import ru.eltex.producthub.qa.domain.TargetType;
import ru.eltex.producthub.qa.service.QuestionService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;
import ru.eltex.producthub.sku.service.SkuService;
import ru.eltex.producthub.task.domain.RelatedType;
import ru.eltex.producthub.task.domain.Task;
import ru.eltex.producthub.task.service.TaskFilter;
import ru.eltex.producthub.task.service.TaskService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class TaskWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    QuestionService questions;
    @Autowired
    TaskService tasks;
    @Autowired
    SkuService skuService;
    @Autowired
    HubUserDetailsService users;
    @Autowired
    SeedImportService importer;

    HubUserDetails product;

    HubUserDetails admin;
    HubUserDetails sales;
    Long skuId;

    @BeforeEach
    void setUp() {
        importer.execute("test");
        product = users.loadUserByUsername("product");
        sales = users.loadUserByUsername("sales");
        admin = users.loadUserByUsername("admin");
        skuId = skuService.findIdByModel("WEP-550K");
    }

    private static String marker() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Task taskFor(Long questionId) {
        List<Task> all = tasks.board(new TaskFilter("all", null, null, null), admin).columns().values().stream()
                .flatMap(List::stream).collect(Collectors.toList());
        return all.stream().filter(t -> t.getRelatedType() == RelatedType.QUESTION && t.getRelatedId().equals(questionId))
                .findFirst().orElseThrow();
    }

    // ---------------------------------------------------------- доска

    @Test
    @WithUserDetails("sales")
    void salesIsSentToMyQuestionsInsteadOfBoard() throws Exception {
        mvc.perform(get("/tasks")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/boards"));
        mvc.perform(get("/questions/my")).andExpect(status().isOk());
        mvc.perform(get("/tasks/new")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("admin")
    void productSeesFourColumnsAndCards() throws Exception {
        var q = questions.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Для доски " + marker(), false, sales);
        Task t = taskFor(q.getId());
        mvc.perform(get("/tasks?assignee=all")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Новые")))
                .andExpect(content().string(containsString("В работе")))
                .andExpect(content().string(containsString("На проверке")))
                .andExpect(content().string(containsString("Закрыто")))
                .andExpect(content().string(containsString("data-id=\"" + t.getId() + "\"")))
                .andExpect(content().string(containsString("task-card")))
                .andExpect(content().string(containsString("Sortable.min.js")));
        mvc.perform(get("/tasks?assignee=all").header("HX-Request", "true"))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    @WithUserDetails("head")
    void headSeesBoardButCannotDrag() throws Exception {
        var q = questions.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Для руководителя " + marker(), false, sales);
        Task t = taskFor(q.getId());
        String html = mvc.perform(get("/tasks?assignee=all")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("data-id=\"" + t.getId() + "\""));
        assertTrue(!html.contains("task-card movable"), "руководитель карточки не двигает");
        mvc.perform(post("/tasks/" + t.getId() + "/status").with(csrf()).param("status", "IN_PROGRESS")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("admin")
    void dragRequestChangesStatusAndRuleReturnsConflictWithMessage() throws Exception {
        var q = questions.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Перетащить " + marker(), false, sales);
        Task t = taskFor(q.getId());
        mvc.perform(post("/tasks/" + t.getId() + "/status").with(csrf()).param("status", "IN_PROGRESS")).andExpect(status().isNoContent());
        assertEquals("IN_PROGRESS", tasks.detail(t.getId(), admin).getStatus().name());
        mvc.perform(post("/tasks/" + t.getId() + "/status").with(csrf()).param("status", "DONE"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Сначала ответьте на вопрос")));
        assertEquals("IN_PROGRESS", tasks.detail(t.getId(), admin).getStatus().name());
        mvc.perform(post("/tasks/" + t.getId() + "/status").param("status", "REVIEW")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("admin")
    void taskPageShowsQuestionAndAnswerFormThatClosesTask() throws Exception {
        var q = questions.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Ответить из задачи " + marker(), false, sales);
        Task t = taskFor(q.getId());
        mvc.perform(get("/tasks/" + t.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Ответить из задачи")))
                .andExpect(content().string(containsString("/questions/" + q.getId() + "/answer")));
        mvc.perform(post("/questions/" + q.getId() + "/answer").with(csrf()).param("answer", "Ответ из задачи")
                        .param("publishToFaq", "true").param("back", "/tasks/" + t.getId()))
                .andExpect(redirectedUrl("/tasks/" + t.getId()));
        mvc.perform(get("/tasks/" + t.getId())).andExpect(content().string(containsString("Ответ из задачи")))
                .andExpect(content().string(containsString("Закрыто")));
    }

    @Test
    @WithUserDetails("product")
    void manualTaskThroughForm() throws Exception {
        String title = "Ручная " + marker();
        mvc.perform(post("/tasks").with(csrf()).param("title", title).param("type", "MANUAL")
                        .param("assigneeId", String.valueOf(product.getId())).param("priority", "HIGH"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/tasks")).andExpect(content().string(containsString(title)));
        mvc.perform(post("/tasks").with(csrf()).param("title", "")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Обязательное поле")));
    }

    @Test
    @WithUserDetails("kuznetsov")
    void headerShowsCounterOfNewTasks() throws Exception {
        questions.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Для счетчика " + marker(), false, sales);
        mvc.perform(get("/catalog")).andExpect(content().string(containsString("class=\"badge\"")));
    }

    // ---------------------------------------------------------- блок вопросов в карточке

    @Test
    @WithUserDetails("sales")
    void skuCardShowsQuestionFormAndFaq() throws Exception {
        var q = questions.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "FAQ вопрос " + marker(), false, sales);
        questions.answer(q.getId(), "FAQ ответ", true, product);
        mvc.perform(get("/sku/WEP-550K")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Вопросы и правки")))
                .andExpect(content().string(containsString("FAQ вопрос")))
                .andExpect(content().string(containsString("FAQ ответ")))
                .andExpect(content().string(containsString("name=\"targetType\"")))
                .andExpect(content().string(containsString("/questions/similar")));
    }

    @Test
    @WithUserDetails("sales")
    void salesDoesNotSeeAnswerFormButProductDoes() throws Exception {
        var q = questions.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Открытый " + marker(), false, sales);
        mvc.perform(get("/sku/WEP-550K")).andExpect(content().string(containsString("Открытый")))
                .andExpect(content().string(not(containsString("/questions/" + q.getId() + "/answer"))));
    }

    @Test
    @WithUserDetails("product")
    void productSeesAnswerFormOnCard() throws Exception {
        var q = questions.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Для продакта " + marker(), false, sales);
        mvc.perform(get("/sku/WEP-550K")).andExpect(content().string(containsString("/questions/" + q.getId() + "/answer")));
    }

    @Test
    @WithUserDetails("sales")
    void askThroughHtmxReturnsUpdatedBlock() throws Exception {
        String text = "Через форму " + marker();
        mvc.perform(post("/questions").with(csrf()).header("HX-Request", "true").param("targetType", "SKU")
                        .param("targetId", String.valueOf(skuId)).param("kind", "QUESTION").param("text", text))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Вопрос отправлен")))
                .andExpect(content().string(containsString(text)))
                .andExpect(content().string(not(containsString("<html"))));
        mvc.perform(post("/questions").with(csrf()).header("HX-Request", "true").param("targetType", "SKU")
                        .param("targetId", String.valueOf(skuId)).param("kind", "QUESTION").param("text", "  "))
                .andExpect(content().string(containsString("Обязательное поле")));
    }

    @Test
    @WithUserDetails("sales")
    void askingRequiresCsrfToken() throws Exception {
        mvc.perform(post("/questions").param("targetType", "SKU").param("targetId", String.valueOf(skuId)).param("text", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("partner")
    void partnerCannotAsk() throws Exception {
        mvc.perform(post("/questions").with(csrf()).param("targetType", "SKU").param("targetId", String.valueOf(skuId))
                .param("text", "можно?")).andExpect(status().isForbidden());
        mvc.perform(get("/questions/my")).andExpect(status().isForbidden());
    }

    @Autowired
    ru.eltex.producthub.firmware.service.FirmwareService firmwareService;
    @Autowired
    ru.eltex.producthub.artifact.service.ArtifactService artifactService;
    @Autowired
    ru.eltex.producthub.artifact.service.ArtifactTypeService artifactTypes;
    @Autowired
    ru.eltex.producthub.sku.repo.SkuRepository skuRepository;

    @Test
    @WithUserDetails("sales")
    void artifactAndFirmwarePagesHaveQuestionBlockAndCreateTasks() throws Exception {
        var form = new ru.eltex.producthub.firmware.service.FirmwareForm();
        form.setVersion("5." + marker());
        form.setSkus("WEP-550K");
        form.setStatus("BETA");
        var fw = firmwareService.create(form, new org.springframework.mock.web.MockMultipartFile("file", "f.bin",
                "application/octet-stream", "x".getBytes()), product).firmware();
        mvc.perform(get("/firmware/" + fw.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Вопросы и правки")))
                .andExpect(content().string(containsString("value=\"FIRMWARE\"")));

        var sku = skuRepository.findCardById(skuId).orElseThrow();
        var artifact = artifactService.create(new ru.eltex.producthub.artifact.service.ArtifactDraft("Материал " + marker(), null,
                artifactTypes.byName("Даташит").orElseThrow(), ru.eltex.producthub.artifact.domain.Language.NONE,
                ru.eltex.producthub.artifact.domain.ArtifactStatus.ACTUAL, sku.getOwner(), null, null, null, List.of(sku),
                List.of(), null), List.of(ru.eltex.producthub.artifact.ArtifactTestSupport.file("m.pdf",
                ru.eltex.producthub.artifact.ArtifactTestSupport.pdf())), null, product);
        mvc.perform(get("/artifacts/" + artifact.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Вопросы и правки")))
                .andExpect(content().string(containsString("value=\"ARTIFACT\"")));

        // вопрос по прошивке попадает владельцу публикации (product), по материалу владельцу материала
        var fq = questions.ask(QuestionKind.QUESTION, TargetType.FIRMWARE, fw.getId(), "По прошивке " + marker(), false, sales);
        assertEquals("product", taskFor(fq.getId()).getAssignee().getLogin());
        var aq = questions.ask(QuestionKind.QUESTION, TargetType.ARTIFACT, artifact.getId(), "По материалу " + marker(), false, sales);
        assertEquals("kuznetsov", taskFor(aq.getId()).getAssignee().getLogin());
    }

    // ---------------------------------------------------------- сквозной путь

    @Test
    @WithUserDetails("sales")
    void fullPathQuestionTaskAnswerFaqAndSimilarSuggestion() throws Exception {
        String marker = marker();
        String text = "Поддерживает ли точка Passpoint " + marker + " на прошивке 2.0?";
        // 1. задан вопрос через форму
        mvc.perform(post("/questions").with(csrf()).header("HX-Request", "true").param("targetType", "SKU")
                .param("targetId", String.valueOf(skuId)).param("kind", "QUESTION").param("text", text)).andExpect(status().isOk());
        var asked = questions.myQuestions(sales).stream().filter(q -> q.getText().equals(text)).findFirst().orElseThrow();
        // 2. владелец получил задачу
        Task task = taskFor(asked.getId());
        assertEquals("kuznetsov", task.getAssignee().getLogin());
        // 3. ответ закрывает задачу и попадает в FAQ
        questions.answer(asked.getId(), "Да, начиная с 2.0", true, product);
        assertEquals("DONE", tasks.detail(task.getId(), admin).getStatus().name());
        mvc.perform(get("/sku/WEP-550K")).andExpect(content().string(containsString("Да, начиная с 2.0")));
        // 4. при похожем вопросе подсказывается готовый ответ
        mvc.perform(get("/questions/similar").param("targetType", "SKU").param("targetId", String.valueOf(skuId))
                        .param("text", "Passpoint " + marker + " прошивка"))
                .andExpect(content().string(containsString("Возможно, ответ уже есть")))
                .andExpect(content().string(containsString("Да, начиная с 2.0")));
        // 5. автор видит ответ в "Мои вопросы"
        mvc.perform(get("/questions/my")).andExpect(content().string(containsString("Да, начиная с 2.0")));
    }

    @Test
    @WithUserDetails("product")
    void backParameterCannotLeaveTheSite() throws Exception {
        var q = questions.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Редирект " + marker(), false, sales);
        mvc.perform(post("/questions/" + q.getId() + "/answer").with(csrf()).param("answer", "ok")
                        .param("back", "https://evil.example/x"))
                .andExpect(redirectedUrl("/questions/my"));
    }

    // ---------------------------------------------------------- ежедневные проверки

    @Test
    @WithUserDetails("admin")
    void adminRunsDailyChecksManually() throws Exception {
        mvc.perform(post("/directories/daily-checks").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/directories")).andExpect(content().string(containsString("Ежедневные проверки")));
    }

    @Test
    @WithUserDetails("product")
    void productCannotRunDailyChecksButSeesNoButton() throws Exception {
        mvc.perform(post("/directories/daily-checks").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/directories")).andExpect(content().string(not(containsString("daily-checks"))));
    }
}
