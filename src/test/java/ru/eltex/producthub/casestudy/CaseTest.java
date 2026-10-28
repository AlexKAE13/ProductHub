package ru.eltex.producthub.casestudy;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.pdf;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.unique;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.eltex.producthub.casestudy.domain.CaseStudy;
import ru.eltex.producthub.casestudy.service.CaseFilter;
import ru.eltex.producthub.casestudy.service.CaseForm;
import ru.eltex.producthub.casestudy.service.CaseService;
import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.importer.SeedImportService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;
import ru.eltex.producthub.tag.service.TagService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class CaseTest {

    @Autowired
    CaseService service;
    @Autowired
    HubUserDetailsService users;
    @Autowired
    SeedImportService importer;
    @Autowired
    TagService tags;
    @Autowired
    MockMvc mvc;

    HubUserDetails product;
    HubUserDetails head;
    HubUserDetails sales;
    HubUserDetails partner;
    HubUserDetails admin;

    @BeforeEach
    void setUp() {
        importer.execute("test");
        product = users.loadUserByUsername("product");
        head = users.loadUserByUsername("head");
        sales = users.loadUserByUsername("sales");
        partner = users.loadUserByUsername("partner");
        admin = users.loadUserByUsername("admin");
    }

    private CaseForm form(String title, String skus, boolean customerVisible, boolean publication) {
        CaseForm f = new CaseForm();
        f.setTitle(title);
        f.setCountry("Казахстан");
        f.setCity("Алматы");
        f.setCustomerName("ТОО Секретный заказчик");
        f.setCustomerVisible(customerVisible);
        f.setSummary("Покрытие торгового центра");
        f.setDeploymentSize("300 точек доступа");
        f.setYear("2026");
        f.setOwnerId(product.getId());
        f.setSkus(skus);
        f.setPublicationAllowed(publication);
        return f;
    }

    private CaseStudy create(String title, String skus, boolean customerVisible, boolean publication) {
        return service.create(form(title, skus, customerVisible, publication), product);
    }

    // ----------------------------------------------------------- критерий этапа: кейс виден в карточках своих SKU

    @Test
    void caseIsVisibleInCardsOfItsSkus() {
        String title = unique("Кейс");
        create(title, "WEP-30L, WLC-3200", true, false);
        assertTrue(service.forSku(skuId("WEP-30L"), sales).cases().stream().anyMatch(c -> c.getTitle().equals(title)));
        assertTrue(service.forSku(skuId("WLC-3200"), sales).cases().stream().anyMatch(c -> c.getTitle().equals(title)));
        assertFalse(service.forSku(skuId("WEP-550K"), sales).cases().stream().anyMatch(c -> c.getTitle().equals(title)));
    }

    @Autowired
    ru.eltex.producthub.sku.service.SkuService skuService;
    @Autowired
    ru.eltex.producthub.task.service.TaskService taskService;

    // ----------------------------------------------------------- проверка нового кейса владельцем SKU

    @Test
    @org.springframework.transaction.annotation.Transactional
    void caseAddedBySalesCreatesReviewTaskForSkuOwnerButNotForOwnCase() {
        CaseStudy bySales = service.create(form(unique("Кейс продажника"), "WEP-30L", false, false), sales);
        var tasks = taskService.openOfType(ru.eltex.producthub.task.domain.TaskType.REVIEW_CASE).stream()
                .filter(t -> bySales.getId().equals(t.getRelatedId())).toList();
        assertFalse(bySales.isPublicationAllowed(), "продажник не может открыть кейс партнерам");
        assertFalse(bySales.isCustomerVisible(), "продажник не может назвать заказчика");
        assertEquals(1, tasks.size(), "владелец SKU получил одну задачу на проверку");
        assertEquals(ru.eltex.producthub.task.domain.RelatedType.CASE, tasks.get(0).getRelatedType());
        assertTrue(tasks.get(0).getTitle().startsWith("Проверить кейс"));
        assertFalse(tasks.get(0).getAssignee().getLogin().equals("sales"), "задача не продажнику, а владельцу");

        // кейс добавил сам владелец SKU: проверять нечего
        HubUserDetails owner = users.loadUserByUsername(tasks.get(0).getAssignee().getLogin());
        CaseStudy byOwner = service.create(form(unique("Кейс владельца"), "WEP-30L", false, false), owner);
        assertTrue(taskService.openOfType(ru.eltex.producthub.task.domain.TaskType.REVIEW_CASE).stream()
                .noneMatch(t -> byOwner.getId().equals(t.getRelatedId())), "своему кейсу задача не создается");
    }

    private Long skuId(String model) {
        return skuService.findIdByModel(model);
    }

    // ----------------------------------------------------------- имя заказчика

    @Test
    void closedCustomerNameIsVisibleOnlyToProductHeadAdmin() {
        CaseStudy c = create(unique("Закрытый"), "WEP-30L", false, false);
        assertEquals("ТОО Секретный заказчик", service.detail(c.getId(), product).customerName());
        assertEquals("ТОО Секретный заказчик", service.detail(c.getId(), head).customerName());
        assertEquals("ТОО Секретный заказчик", service.detail(c.getId(), admin).customerName());
        var forSales = service.detail(c.getId(), sales);
        assertNull(forSales.customerName(), "продажам имя закрытого заказчика не отдается вообще");
        assertTrue(forSales.customerHidden());
        assertNull(service.detail(c.getId(), users.loadUserByUsername("rnd")).customerName());
        assertNull(service.detail(c.getId(), users.loadUserByUsername("service")).customerName());
    }

    @Test
    void openCustomerNameIsVisibleToEveryone() {
        CaseStudy c = create(unique("Открытый"), "WEP-30L", true, false);
        assertEquals("ТОО Секретный заказчик", service.detail(c.getId(), sales).customerName());
        assertFalse(service.detail(c.getId(), sales).customerHidden());
    }

    @Test
    @WithUserDetails("sales")
    void closedNameNeverReachesHtmlForSales() throws Exception {
        CaseStudy c = create(unique("Html"), "WEP-30L", false, false);
        mvc.perform(get("/cases/" + c.getId())).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Секретный заказчик"))))
                .andExpect(content().string(containsString("скрыт")));
        mvc.perform(get("/cases?q=" + c.getTitle())).andExpect(content().string(not(containsString("Секретный заказчик"))));
        mvc.perform(get("/sku/WEP-30L")).andExpect(content().string(not(containsString("Секретный заказчик"))));
    }

    @Test
    @WithUserDetails("head")
    void headSeesClosedNameInHtml() throws Exception {
        CaseStudy c = create(unique("Руководитель"), "WEP-30L", false, false);
        mvc.perform(get("/cases/" + c.getId())).andExpect(content().string(containsString("Секретный заказчик")));
    }

    // ----------------------------------------------------------- права и контур

    @Test
    void onlyProductAndAdminEditAndSalesOnlyCreate() {
        assertThrows(AccessDeniedException.class, () -> service.create(form("x", "WEP-30L", true, false), head));
        assertThrows(AccessDeniedException.class,
                () -> service.create(form("x", "WEP-30L", true, false), users.loadUserByUsername("service")));
        assertNotNull(service.create(form(unique("Админ"), "WEP-30L", true, false), admin).getId());
        CaseStudy c = create(unique("Правка"), "WEP-30L", true, false);
        assertThrows(AccessDeniedException.class, () -> service.update(c.getId(), form("новое", "WEP-30L", true, false), sales));
        assertThrows(AccessDeniedException.class, () -> service.delete(c.getId(), sales));
    }

    @Test
    void partnerSeesOnlyCasesAllowedForPublication() {
        CaseStudy closed = create(unique("Внутренний"), "WEP-30L", true, false);
        CaseStudy open = create(unique("Публичный"), "WEP-30L", true, true);
        assertThrows(AccessDeniedException.class, () -> service.detail(closed.getId(), partner));
        assertNotNull(service.detail(open.getId(), partner));
        var forPartner = service.forSku(skuId("WEP-30L"), partner).cases();
        assertTrue(forPartner.stream().anyMatch(c -> c.getId().equals(open.getId())));
        assertTrue(forPartner.stream().noneMatch(c -> c.getId().equals(closed.getId())));
        assertTrue(service.search(new CaseFilter(null, null, null, null, null, 0), partner).items().stream()
                .allMatch(CaseStudy::isPublicationAllowed));
    }

    // ----------------------------------------------------------- теги вместо отрасли, фильтры

    @Test
    void casesAreTaggedAndFilteredByTags() {
        var retail = tags.resolve("Ритейл", null).orElseThrow();
        CaseForm f = form(unique("Ритейл"), "WEP-30L", true, false);
        f.getTagIds().add(retail.getId());
        CaseStudy tagged = service.create(f, product);
        CaseStudy plain = create(unique("Без тега"), "WEP-30L", true, false);
        var found = service.search(new CaseFilter(null, null, retail.getId(), null, null, 0), product).items();
        assertTrue(found.stream().anyMatch(c -> c.getId().equals(tagged.getId())));
        assertTrue(found.stream().noneMatch(c -> c.getId().equals(plain.getId())));
        assertEquals("Ритейл", service.detail(tagged.getId(), product).tags().get(0).getValue());
    }

    @Test
    void filtersByCountryYearAndSku() {
        CaseForm f = form(unique("Фильтр"), "WLC-3200", true, false);
        f.setCountry("Узбекистан");
        f.setYear("2024");
        CaseStudy c = service.create(f, product);
        assertTrue(service.search(new CaseFilter("Узбекистан", 2024, null, "wlc3200", null, 0), sales).items().stream()
                .anyMatch(x -> x.getId().equals(c.getId())));
        assertTrue(service.search(new CaseFilter("Узбекистан", 2023, null, null, null, 0), sales).items().stream()
                .noneMatch(x -> x.getId().equals(c.getId())));
        assertTrue(service.countries().contains("Узбекистан"));
        assertTrue(service.years().contains(2024));
    }

    // ----------------------------------------------------------- проверка данных, аудит, вложения

    @Test
    void requiredFieldsAndSkuAreChecked() {
        var errors = assertThrows(FormValidationException.class, () -> service.create(new CaseForm(), product)).getErrors();
        assertTrue(errors.keySet().containsAll(List.of("title", "country", "ownerId", "skus")), errors.toString());
        CaseForm bad = form(unique("Плохой"), "НЕТ-ТАКОГО", true, false);
        assertTrue(assertThrows(FormValidationException.class, () -> service.create(bad, product)).getErrors().containsKey("skus"));
        CaseForm year = form(unique("Год"), "WEP-30L", true, false);
        year.setYear("abc");
        assertTrue(assertThrows(FormValidationException.class, () -> service.create(year, product)).getErrors().containsKey("year"));
    }

    @Test
    void changesAreWrittenToAudit() {
        CaseStudy c = create(unique("Аудит"), "WEP-30L", false, false);
        CaseForm f = service.formFor(c.getId(), product);
        f.setCustomerVisible(true);
        f.setDeploymentSize("500 точек");
        service.update(c.getId(), f, product);
        var history = service.detail(c.getId(), product).history();
        assertTrue(history.stream().anyMatch(h -> "customerVisible".equals(h.getField()) && "true".equals(h.getNewValue())));
        assertTrue(history.stream().anyMatch(h -> "deploymentSize".equals(h.getField())));
        assertTrue(history.stream().anyMatch(h -> h.getAction().name().equals("CREATE")));
    }

    @Test
    void attachmentsAreStoredAsArtifactsAndCanBeRemoved() {
        CaseStudy c = create(unique("Вложения"), "WEP-30L", true, false);
        var file = new MockMultipartFile("files", "case.pdf", "application/pdf", pdf());
        assertEquals(1, service.addAttachments(c.getId(), List.of(file), product));
        var attachments = service.detail(c.getId(), product).attachments();
        assertEquals(1, attachments.size());
        assertEquals("Кейс / референс внедрения", attachments.get(0).artifact().getType().getName());
        assertEquals("case.pdf", attachments.get(0).main().getFileName());

        service.removeAttachment(c.getId(), attachments.get(0).artifact().getId(), product);
        assertTrue(service.detail(c.getId(), product).attachments().isEmpty());
    }

    @Test
    void attachmentWithWrongContentIsRejected() {
        CaseStudy c = create(unique("Подмена"), "WEP-30L", true, false);
        var fake = new MockMultipartFile("files", "case.pdf", "application/pdf", "не pdf".getBytes());
        assertThrows(FormValidationException.class, () -> service.addAttachments(c.getId(), List.of(fake), product));
    }

    @Test
    void softDeleteHidesCase() {
        CaseStudy c = create(unique("Удалить"), "WEP-30L", true, false);
        service.delete(c.getId(), product);
        assertThrows(RuntimeException.class, () -> service.detail(c.getId(), product));
    }

    @Test
    @WithUserDetails("sales")
    void salesSeesCreateButtonAndOpensFormWithoutPublicationCheckboxes() throws Exception {
        mvc.perform(get("/cases")).andExpect(status().isOk()).andExpect(content().string(containsString("/cases/new")));
        mvc.perform(get("/cases/new")).andExpect(status().isOk())
                .andExpect(content().string(containsString("upload-input")))
                .andExpect(content().string(not(containsString("name=\"publicationAllowed\""))));
    }

    @Test
    @WithUserDetails("partner")
    void partnerCannotOpenCreateForm() throws Exception {
        mvc.perform(get("/cases/new")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("sales")
    void skuCardShowsCaseBlock() throws Exception {
        String title = unique("Блок7");
        create(title, "WEP-30L", true, false);
        mvc.perform(get("/sku/WEP-30L")).andExpect(content().string(containsString(title)))
                .andExpect(content().string(containsString("Казахстан")))
                .andExpect(content().string(containsString("300 точек доступа")));
    }
}
