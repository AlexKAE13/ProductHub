package ru.eltex.producthub.artifact;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.file;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.pdf;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.unique;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.artifact.domain.ArtifactStatus;
import ru.eltex.producthub.artifact.domain.Language;
import ru.eltex.producthub.artifact.service.ArtifactDraft;
import ru.eltex.producthub.artifact.service.ArtifactForm;
import ru.eltex.producthub.artifact.service.ArtifactService;
import ru.eltex.producthub.artifact.service.ArtifactTypeService;
import ru.eltex.producthub.artifact.service.SkuMaterialsService;
import ru.eltex.producthub.importer.SeedImportService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.repo.SkuRepository;
import ru.eltex.producthub.sku.service.ModelNormalizer;
import ru.eltex.producthub.sku.service.SkuService;
import ru.eltex.producthub.tag.service.TagService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class ArtifactWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ArtifactService service;
    @Autowired
    ArtifactTypeService types;
    @Autowired
    HubUserDetailsService users;
    @Autowired
    SkuRepository skus;
    @Autowired
    SkuService skuService;
    @Autowired
    SkuMaterialsService materials;
    @Autowired
    TagService tagService;
    @Autowired
    SeedImportService importer;
    @Autowired
    EntityManagerFactory emf;

    HubUserDetails product;

    @BeforeEach
    void setUp() {
        importer.execute("test");
        product = users.loadUserByUsername("product");
    }

    private Sku sku(String model) {
        return skus.findByModelNormalized(ModelNormalizer.normalize(model)).orElseThrow();
    }

    private Artifact create(String title, String type, Language language, Sku sku, String fileName, byte[] content) {
        return service.create(new ArtifactDraft(title, null, types.byName(type).orElseThrow(), language,
                ArtifactStatus.ACTUAL, sku.getOwner(), null, null, null, List.of(sku), List.of(), null),
                List.of(file(fileName, content)), null, product);
    }

    // ----------------------------------------------------------- файлы: Range, ETag, безопасность

    @Test
    @WithUserDetails("sales")
    void fileIsServedWithEtagRangeAndSafeHeaders() throws Exception {
        byte[] content = pdf();
        Artifact a = create(unique("Файл"), "Даташит", Language.RU, sku("WEP-550K"), "datasheet.pdf", content);
        Long fileId = service.detail(a.getId(), product).currentFiles().get(0).getId();
        String sha = service.detail(a.getId(), product).currentFiles().get(0).getSha256();

        var ok = mvc.perform(get("/artifacts/files/" + fileId))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + sha + "\""))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertEquals(content.length, ok.getResponse().getContentAsByteArray().length);

        mvc.perform(get("/artifacts/files/" + fileId).header("If-None-Match", "\"" + sha + "\""))
                .andExpect(status().isNotModified());

        mvc.perform(get("/artifacts/files/" + fileId).header("Range", "bytes=0-3"))
                .andExpect(status().isPartialContent())
                .andExpect(content().bytes("%PDF".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(get("/artifacts/files/" + fileId + "?inline=true"))
                .andExpect(header().string("Content-Disposition", containsString("inline")));
    }

    @Test
    @WithUserDetails("sales")
    void svgIsNeverShownInline() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        Artifact a = service.create(new ArtifactDraft(unique("Svg"), null, types.byName("Фото").orElseThrow(), Language.NONE,
                ArtifactStatus.ACTUAL, sku("WEP-550K").getOwner(), null, null, null, List.of(sku("WEP-550K")), List.of(), null),
                List.of(file("logo.svg", svg)), null, product);
        Long fileId = service.detail(a.getId(), product).currentFiles().get(0).getId();
        mvc.perform(get("/artifacts/files/" + fileId + "?inline=true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
    }

    @Test
    @WithUserDetails("sales")
    void textFilesPreviewAsPlainTextAndOfficeFilesStayDownloads() throws Exception {
        byte[] text = "Заметки к релизу".getBytes(StandardCharsets.UTF_8);
        Artifact a = service.create(new ArtifactDraft(unique("Текст"), null, types.byName("Фото").orElseThrow(), Language.NONE,
                ArtifactStatus.ACTUAL, sku("WEP-550K").getOwner(), null, null, null, List.of(sku("WEP-550K")), List.of(), null),
                List.of(file("notes.txt", text)), null, product);
        Long fileId = service.detail(a.getId(), product).currentFiles().get(0).getId();
        mvc.perform(get("/artifacts/files/" + fileId + "?inline=true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Content-Type", containsString("text/plain")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
        mvc.perform(get("/artifacts/files/" + fileId))
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
        assertTrue(ru.eltex.producthub.artifact.storage.FileTypes.isInlineSafe("video.MP4"));
        assertFalse(ru.eltex.producthub.artifact.storage.FileTypes.isInlineSafe("deck.pptx"), "документы Office браузер не откроет");
        assertFalse(ru.eltex.producthub.artifact.storage.FileTypes.isInlineSafe("logo.svg"));
    }

    @Test
    @WithUserDetails("product")
    void bulkFormIsNotBrokenByNestedForms() throws Exception {
        create(unique("Вложенные"), "Даташит", Language.RU, sku("WEP-550K"), "n1.pdf", pdf());
        create(unique("Вложенные"), "Даташит", Language.RU, sku("WEP-550K"), "n2.pdf", pdf());
        String html = mvc.perform(get("/artifacts")).andReturn().getResponse().getContentAsString();
        int start = html.indexOf("id=\"bulkForm\"");
        int end = html.indexOf("</form>", start);
        String inside = html.substring(start, end);
        // внутри формы массовых действий нет других форм: браузер закрыл бы ее на первой вложенной, и выбор слетел бы
        assertEquals(-1, inside.indexOf("<form", 5), "вложенных форм быть не должно");
        // все галочки строк лежат внутри формы
        assertEquals(html.split("class=\"bulk-check bulk-box\"", -1).length, inside.split("class=\"bulk-check bulk-box\"", -1).length);
        assertTrue(inside.contains("formaction=\"/artifacts/"), "удаление строки идет через formaction");
    }

    @Test
    @WithUserDetails("partner")
    void partnerCannotDownloadInternalFile() throws Exception {
        Artifact a = create(unique("Закрытый"), "Даташит", Language.RU, sku("WEP-550K"), "x.pdf", pdf());
        Long fileId = service.detail(a.getId(), product).currentFiles().get(0).getId();
        mvc.perform(get("/artifacts/files/" + fileId)).andExpect(status().isForbidden());
        mvc.perform(get("/artifacts")).andExpect(status().isForbidden());
    }

    // ----------------------------------------------------------- список и страница

    @Test
    @WithUserDetails("sales")
    void listFiltersByTypeLanguageAndSku() throws Exception {
        String title = unique("Список");
        create(title, "Battle card", Language.EN, sku("WEP-550K"), "b.pdf", pdf());
        mvc.perform(get("/artifacts?q=" + title)).andExpect(status().isOk())
                .andExpect(content().string(containsString(title)));
        // проверяем только таблицу результатов (HTMX-фрагмент), в полной странице название есть и в поле поиска
        mvc.perform(get("/artifacts?q=" + title + "&language=RU").header("HX-Request", "true"))
                .andExpect(content().string(not(containsString(title))));
        mvc.perform(get("/artifacts?q=" + title + "&language=EN&sku=wep550k").header("HX-Request", "true"))
                .andExpect(content().string(containsString(title)));
        mvc.perform(get("/artifacts?q=" + title + "&sku=WLC-3200").header("HX-Request", "true"))
                .andExpect(content().string(not(containsString(title))));
        mvc.perform(get("/artifacts?q=" + title).header("HX-Request", "true"))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    @WithUserDetails("sales")
    void listFiltersByProductFamilyOfLineOrSku() throws Exception {
        String title = unique("Семейство");
        create(title, "Battle card", Language.EN, sku("WEP-550K"), "f.pdf", pdf());
        mvc.perform(get("/artifacts?q=" + title + "&family=Wi-Fi").header("HX-Request", "true"))
                .andExpect(content().string(containsString(title)));
        mvc.perform(get("/artifacts?q=" + title + "&family=ESR").header("HX-Request", "true"))
                .andExpect(content().string(not(containsString(title))));
    }

    @Test
    @WithUserDetails("sales")
    void salesSeesNoUploadButtonsOrBulk() throws Exception {
        mvc.perform(get("/artifacts"))
                .andExpect(content().string(not(containsString("/artifacts/upload"))))
                .andExpect(content().string(not(containsString("name=\"ids\""))));
        mvc.perform(get("/artifacts/upload")).andExpect(status().isForbidden());
        mvc.perform(get("/artifacts/import")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("product")
    void productSeesBulkAndImportButtons() throws Exception {
        create(unique("Для массовых"), "Даташит", Language.RU, sku("WEP-550K"), "m.pdf", pdf());
        mvc.perform(get("/artifacts"))
                .andExpect(content().string(containsString("/artifacts/import")))
                .andExpect(content().string(containsString("name=\"ids\"")));
    }

    @Test
    @WithUserDetails("product")
    void artifactPageShowsVersionsAndPreview() throws Exception {
        Artifact a = create(unique("Страница"), "Даташит", Language.RU, sku("WEP-550K"), "p.pdf", pdf());
        service.addVersion(a.getId(), List.of(file("p2.pdf", pdf())), "вторая", product);
        mvc.perform(get("/artifacts/" + a.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("v2")))
                .andExpect(content().string(containsString("v1")))
                .andExpect(content().string(containsString("вторая")))
                .andExpect(content().string(containsString("<iframe")))
                .andExpect(content().string(containsString("Подтвердить актуальность")));
        mvc.perform(get("/artifacts/999999")).andExpect(status().isNotFound());
    }

    @Test
    @WithUserDetails("product")
    void uploadFlowThroughHttp() throws Exception {
        var pdfFile = new MockMultipartFile("files", "WLC-3200_RU.pdf", "application/pdf", pdf());
        var result = mvc.perform(multipart("/artifacts/upload").file(pdfFile).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/artifacts/import/*"))
                .andReturn();
        String url = result.getResponse().getRedirectedUrl();
        mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(content().string(containsString("WLC-3200")))
                .andExpect(content().string(containsString("dryRun")));
        String token = url.substring(url.lastIndexOf('/') + 1);
        var confirm = mvc.perform(post("/artifacts/import/" + token + "/confirm").with(csrf())
                        .param("rows[0].include", "true").param("rows[0].title", "WLC-3200")
                        .param("rows[0].type", "Карточка продукта").param("rows[0].language", "RU")
                        .param("rows[0].status", "ACTUAL").param("rows[0].skus", "WLC-3200")
                        .param("rows[0].contour", "INTERNAL").param("rows[0].ownerId", String.valueOf(product.getId())))
                .andExpect(status().is3xxRedirection()).andReturn();
        mvc.perform(get(confirm.getResponse().getRedirectedUrl())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Создано")));
    }

    @Test
    @WithUserDetails("product")
    void bulkActionThroughHttp() throws Exception {
        Artifact a = create(unique("Массовое"), "Даташит", Language.RU, sku("WEP-550K"), "bulk.pdf", pdf());
        mvc.perform(post("/artifacts/bulk").with(csrf()).param("ids", String.valueOf(a.getId()))
                        .param("action", "STATUS").param("value", "OUTDATED"))
                .andExpect(status().is3xxRedirection());
        assertEquals(ArtifactStatus.OUTDATED, service.detail(a.getId(), product).artifact().getStatus());
    }

    @Test
    @WithUserDetails("product")
    void bulkBarOffersStatusesAndTagsAsLists() throws Exception {
        create(unique("Для списка"), "Даташит", Language.RU, sku("WEP-550K"), "l.pdf", pdf());
        mvc.perform(get("/artifacts").header("HX-Request", "true"))
                .andExpect(content().string(containsString("id=\"bulkStatus\"")))
                .andExpect(content().string(containsString("Актуален")))
                .andExpect(content().string(containsString("Устарел")))
                .andExpect(content().string(containsString("id=\"bulkTag\"")))
                .andExpect(content().string(containsString("Конкурент: Huawei")));
    }

    @Test
    @WithUserDetails("sales")
    void salesCannotPostBulk() throws Exception {
        mvc.perform(post("/artifacts/bulk").with(csrf()).param("ids", "1").param("action", "STATUS")
                .param("value", "OUTDATED")).andExpect(status().isForbidden());
    }

    // ----------------------------------------------------------- материалы в карточке SKU

    @Test
    @WithUserDetails("sales")
    void skuCardGroupsMaterialsByTypeAndFiltersByLanguage() throws Exception {
        Sku s = sku("WEP-3ax");
        String ru = unique("Даташит-RU");
        String en = unique("Даташит-EN");
        String cmp = unique("Сравнение");
        create(ru, "Даташит", Language.RU, s, "ru.pdf", pdf());
        create(en, "Даташит", Language.EN, s, "en.pdf", pdf());
        create(cmp, "Сравнение с конкурентами", Language.EN, s, "cmp.pdf", pdf());

        mvc.perform(get("/sku/WEP-3ax")).andExpect(status().isOk())
                .andExpect(content().string(containsString(ru)))
                .andExpect(content().string(containsString(en)))
                .andExpect(content().string(containsString(cmp)))
                .andExpect(content().string(containsString("Сравнения и battle cards")));

        mvc.perform(get("/skus/" + s.getId() + "/materials?lang=RU"))
                .andExpect(content().string(containsString(ru)))
                .andExpect(content().string(not(containsString(en))));
        mvc.perform(get("/skus/" + s.getId() + "/materials?lang=EN"))
                .andExpect(content().string(containsString(en)))
                .andExpect(content().string(not(containsString(ru))));
    }

    @Test
    void outdatedMaterialsAreCollapsedSeparately() {
        Sku s = sku("WEP-30L");
        Artifact old = create(unique("Старый"), "Даташит", Language.RU, s, "o.pdf", pdf());
        Artifact fresh = create(unique("Свежий"), "Даташит", Language.RU, s, "f.pdf", pdf());
        service.bulk(List.of(old.getId()), ArtifactService.BulkAction.STATUS, "OUTDATED", product);
        var view = materials.forSku(s.getId(), s.getLine().getId(), "ALL", product);
        var datasheets = view.docs().stream().filter(g -> g.type().getName().equals("Даташит")).findFirst().orElseThrow();
        assertTrue(datasheets.actual().stream().anyMatch(i -> i.artifact().getId().equals(fresh.getId())));
        assertTrue(datasheets.outdated().stream().anyMatch(i -> i.artifact().getId().equals(old.getId())));
        assertTrue(datasheets.actual().stream().noneMatch(i -> i.artifact().getId().equals(old.getId())));
    }

    @Test
    void lineLevelMaterialAppearsInAllSkusOfLine() {
        Sku a = sku("WLC-3200");
        Sku b = sku("WLC-15");
        String title = unique("Вся линейка");
        service.create(new ArtifactDraft(title, null, types.byName("Карточка продукта").orElseThrow(), Language.RU,
                ArtifactStatus.ACTUAL, a.getOwner(), null, null, a.getLine(), List.of(), List.of(), null),
                List.of(file("line.pdf", pdf())), null, product);
        for (Sku s : List.of(a, b)) {
            var view = materials.forSku(s.getId(), s.getLine().getId(), "ALL", product);
            assertTrue(view.docs().stream().flatMap(g -> g.actual().stream())
                    .anyMatch(i -> i.artifact().getTitle().equals(title)), s.getModel());
        }
        Sku other = sku("WEP-550K");
        assertTrue(materials.forSku(other.getId(), other.getLine().getId(), "ALL", product).docs().stream()
                .flatMap(g -> g.actual().stream()).noneMatch(i -> i.artifact().getTitle().equals(title)));
    }

    @Test
    void competitorTagIsShownOnComparison() {
        Sku s = sku("WEP-500K");
        var huawei = tagService.resolve("Huawei", null).orElseThrow();
        Artifact a = create(unique("Vs Huawei"), "Сравнение с конкурентами", Language.EN, s, "vs.pdf", pdf());
        ArtifactForm form = service.formFor(a.getId(), product);
        form.getTagIds().add(huawei.getId());
        service.update(a.getId(), form, product);
        var view = materials.forSku(s.getId(), s.getLine().getId(), "ALL", product);
        var item = view.compare().actual().stream().filter(i -> i.artifact().getId().equals(a.getId())).findFirst().orElseThrow();
        assertEquals("Huawei", item.competitors().get(0).getValue());
    }

    @Test
    void partnerSeesOnlyExternalMaterialsInCard() {
        Sku s = sku("WEP-3L");
        HubUserDetails partner = users.loadUserByUsername("partner");
        create(unique("Закрыт"), "Даташит", Language.RU, s, "closed.pdf", pdf());
        assertTrue(materials.forSku(s.getId(), s.getLine().getId(), "ALL", partner).isEmpty());
    }

    // ----------------------------------------------------------- скорость: нет N+1

    @Test
    void materialsBlockUsesFixedNumberOfQueries() {
        Sku s = sku("WEP-2ac");
        for (int i = 0; i < 12; i++) {
            create(unique("Мат" + i), i % 2 == 0 ? "Даташит" : "Battle card", i % 3 == 0 ? Language.RU : Language.EN, s,
                    "m" + i + ".pdf", pdf());
        }
        materials.forSku(s.getId(), s.getLine().getId(), "ALL", product);
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var view = materials.forSku(s.getId(), s.getLine().getId(), "ALL", product);
        assertFalse(view.docs().isEmpty());
        assertFalse(view.compare().actual().isEmpty(), "Battle card показывается в блоке сравнений");
        assertTrue(stats.getPrepareStatementCount() <= 4, "запросов на блоки материалов: " + stats.getPrepareStatementCount());
    }

    @Test
    void wholeSkuCardWithManyMaterialsStaysFast() {
        Sku s = sku("WEP-1L");
        for (int i = 0; i < 50; i++) {
            create(unique("Карточка" + i), i % 2 == 0 ? "Даташит" : "Руководство по эксплуатации / настройке",
                    i % 2 == 0 ? Language.RU : Language.EN, s, "k" + i + ".pdf", pdf());
        }
        skuService.loadCard(s.getModel(), product);
        materials.forSku(s.getId(), s.getLine().getId(), "ALL", product);
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        skuService.loadCard(s.getModel(), product);
        materials.forSku(s.getId(), s.getLine().getId(), "ALL", product);
        assertTrue(stats.getPrepareStatementCount() <= 10, "запросов на карточку с 50 материалами: " + stats.getPrepareStatementCount());
    }
}
