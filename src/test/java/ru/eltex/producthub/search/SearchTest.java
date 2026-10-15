package ru.eltex.producthub.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.file;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.pdf;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.artifact.domain.ArtifactStatus;
import ru.eltex.producthub.artifact.domain.Language;
import ru.eltex.producthub.artifact.service.ArtifactDraft;
import ru.eltex.producthub.artifact.service.ArtifactForm;
import ru.eltex.producthub.artifact.service.ArtifactService;
import ru.eltex.producthub.artifact.service.ArtifactTypeService;
import ru.eltex.producthub.casestudy.service.CaseForm;
import ru.eltex.producthub.casestudy.service.CaseService;
import ru.eltex.producthub.firmware.service.FirmwareForm;
import ru.eltex.producthub.firmware.service.FirmwareService;
import ru.eltex.producthub.importer.SeedImportService;
import ru.eltex.producthub.qa.domain.QuestionKind;
import ru.eltex.producthub.qa.domain.TargetType;
import ru.eltex.producthub.qa.service.QuestionService;
import ru.eltex.producthub.search.service.SearchIndexer;
import ru.eltex.producthub.search.service.SearchLogService;
import ru.eltex.producthub.search.service.SearchService;
import ru.eltex.producthub.search.service.SearchService.Group;
import ru.eltex.producthub.search.service.SearchService.Hit;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;
import ru.eltex.producthub.sku.repo.SkuRepository;
import ru.eltex.producthub.sku.service.ModelNormalizer;

/** Критерии этапа 5: поиск по артикулу с нормализацией, морфология, опечатки, права, обновление индекса, журнал. */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
class SearchTest {

    @Autowired
    SearchService search;
    @Autowired
    SearchIndexer indexer;
    @Autowired
    SearchLogService searchLog;
    @Autowired
    ArtifactService artifacts;
    @Autowired
    ArtifactTypeService types;
    @Autowired
    FirmwareService firmware;
    @Autowired
    CaseService cases;
    @Autowired
    QuestionService questions;
    @Autowired
    SkuRepository skus;
    @Autowired
    SeedImportService importer;
    @Autowired
    HubUserDetailsService users;
    @Autowired
    JdbcTemplate jdbc;

    HubUserDetails product;
    HubUserDetails sales;

    @BeforeEach
    void setUp() {
        importer.execute("test");
        product = users.loadUserByUsername("product");
        sales = users.loadUserByUsername("sales");
    }

    private static String token() {
        return "q" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private Group group(String q, String type, boolean internal) {
        return search.suggest(q, internal).groups().stream().filter(g -> g.type().equals(type)).findFirst()
                .orElse(new Group(type, 0, List.of()));
    }

    private boolean found(String q, String type, String title, boolean internal) {
        return group(q, type, internal).hits().stream().anyMatch(h -> h.title().equals(title));
    }

    private Artifact artifact(String title, String type) {
        ArtifactDraft d = new ArtifactDraft(title, null, types.byName(type).orElseThrow(), Language.RU, ArtifactStatus.ACTUAL,
                skus.findByModelNormalized("WEP550K").orElseThrow().getOwner(), null, null, null, List.of(), List.of(), null);
        return artifacts.create(d, List.of(file(title + ".pdf", pdf())), null, product);
    }

    // ----------------------------------------------------------- артикулы

    @Test
    void modelVariantsFindSameSkuFirst() {
        for (String q : List.of("wep550k", "WEP 550K", "wep-550k", "WEP-550K", "Wep_550k")) {
            Group g = group(q, "SKU", true);
            assertFalse(g.hits().isEmpty(), q);
            assertEquals("WEP-550K", g.hits().get(0).title(), "первым должен быть точный артикул: " + q);
        }
    }

    @Test
    void modelPrefixFindsSku() {
        assertTrue(group("wep55", "SKU", true).hits().stream().anyMatch(h -> h.title().equals("WEP-550K")));
        assertTrue(found("wlc-32", "SKU", "WLC-3200", true));
    }

    @Test
    void exactModelIsFirstEvenWhenOtherObjectsMentionIt() {
        String t = token();
        artifact("Описание " + t + " WEP-550K WEP-550K WEP-550K", "Даташит");
        assertEquals("WEP-550K", group("WEP-550K", "SKU", true).hits().get(0).title());
    }

    // ----------------------------------------------------------- морфология и опечатки

    @Test
    void englishMorphology() {
        String t = token();
        String title = "Configuring wireless controllers " + t;
        artifact(title, "Даташит");
        assertTrue(found("configuration controller " + t, "ARTIFACT", title, true));
    }

    @Test
    void russianMorphology() {
        String t = token();
        String title = "Настройка контроллеров " + t;
        artifact(title, "Даташит");
        assertTrue(found("настройки контроллер " + t, "ARTIFACT", title, true));
        assertTrue(found("НАСТРОЙКА Контроллеров " + t, "ARTIFACT", title, true), "регистр не важен");
    }

    @Test
    void yoIsTreatedAsYe() {
        String t = token();
        String title = "Твёрдый ответ " + t;
        artifact(title, "Даташит");
        assertTrue(found("тверд ответ " + t, "ARTIFACT", title, true));
        assertTrue(found("твёрдый " + t, "ARTIFACT", title, true));
    }

    @Test
    void typoWithDistanceOneFindsLongWords() {
        String t = token();
        String title = "Radiomodule calibration " + t;
        artifact(title, "Даташит");
        assertTrue(found("radiomodle " + t, "ARTIFACT", title, true), "пропущена буква");
        assertTrue(found("radiomodule calibraton " + t, "ARTIFACT", title, true), "лишняя буква пропущена в втором слове");
    }

    @Test
    void shortWordsAreNotFuzzy() {
        String t = token();
        artifact("Olt " + t, "Даташит");
        assertFalse(found("old " + t, "ARTIFACT", "Olt " + t, true), "для слов короче 4 символов опечатки не допускаются");
    }

    @Test
    void searchAsYouTypePrefixOfLastWord() {
        String t = token();
        String title = "Provisioning handbook " + t;
        artifact(title, "Даташит");
        assertTrue(found(t + " handb", "ARTIFACT", title, true), "последнее слово еще печатается");
    }

    // ----------------------------------------------------------- контур и права

    @Test
    void externalContourSeesOnlyExternalDocuments() {
        String t = token();
        String internalTitle = "Закрытый " + t;
        String externalTitle = "Открытый " + t;
        artifact(internalTitle, "Даташит");
        artifact(externalTitle, "Видео");
        assertTrue(found(t, "ARTIFACT", internalTitle, true));
        assertTrue(found(t, "ARTIFACT", externalTitle, true));
        assertFalse(found(t, "ARTIFACT", internalTitle, false), "внутренний материал не должен попасть во внешний контур");
        assertTrue(found(t, "ARTIFACT", externalTitle, false));
        // Все SKU, которые видит внешний контур, внешние
        for (Hit h : group("WEP", "SKU", false).hits()) {
            assertEquals("EXTERNAL", jdbc.queryForObject("select contour from sku where id = ?", String.class, h.id()));
        }
    }

    @Test
    void firmwareAndCasesFollowTheirPublicFlags() {
        String t = token();
        FirmwareForm f = new FirmwareForm();
        f.setVersion("8." + t);
        f.setSkus("WEP-30L");
        f.setStatus("RELEASE");
        f.setReleaseDate(LocalDate.now().toString());
        f.setReleaseNotes("Поддержка Passpoint " + t);
        f.setPublicRelease(false);
        var saved = firmware.create(f, new MockMultipartFile("file", "a.bin", "application/octet-stream", t.getBytes()), product)
                .firmware();
        assertTrue(group(t, "FIRMWARE", true).hits().stream().anyMatch(h -> h.id() == saved.getId()));
        assertTrue(group("8." + t, "FIRMWARE", true).hits().stream().anyMatch(h -> h.id() == saved.getId()), "по версии");
        assertTrue(group(t, "FIRMWARE", false).hits().isEmpty(), "непубличная прошивка скрыта от внешнего контура");

        CaseForm c = new CaseForm();
        c.setTitle("Кейс " + t);
        c.setCountry("Казахстан");
        c.setCity("Алматы");
        c.setCustomerName("Заказчик" + t);
        c.setCustomerVisible(false);
        c.setSummary("Покрытие склада");
        c.setYear("2026");
        c.setOwnerId(product.getId());
        c.setSkus("WEP-30L");
        c.setPublicationAllowed(false);
        cases.create(c, product);
        assertEquals(1, group(t, "CASE", true).total());
        assertEquals(1, group("Заказчик" + t, "CASE", true).total(), "имя заказчика ищут только внутренние пользователи");
        assertEquals(0, group(t, "CASE", false).total());
    }

    @Test
    void faqIsSearchableOnlyAfterPublishing() {
        String t = token();
        long skuId = skus.findByModelNormalized("WEP550K").orElseThrow().getId();
        var q = questions.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Сколько антенн у модели " + t + "?", false, sales);
        assertEquals(0, group(t, "FAQ", true).total(), "вопрос без ответа в поиск не попадает");
        questions.answer(q.getId(), "Восемь внутренних антенн", true, product);
        Group g = group(t, "FAQ", true);
        assertEquals(1, g.total());
        assertTrue(g.hits().get(0).url().startsWith("/sku/WEP-550K"));
        assertTrue(group("восемь антенн", "FAQ", true).hits().stream().anyMatch(h -> h.id() == q.getId()), "и по тексту ответа");
    }

    // ----------------------------------------------------------- индекс следует за сохранением

    @Test
    void indexFollowsSaveEditAndDelete() {
        String t = token();
        String title = "Первое название " + t;
        Artifact a = artifact(title, "Даташит");
        assertTrue(found(t, "ARTIFACT", title, true));

        ArtifactForm form = artifacts.formFor(a.getId(), product);
        String renamed = "Второе название " + t;
        form.setTitle(renamed);
        artifacts.update(a.getId(), form, product);
        assertFalse(found(t, "ARTIFACT", title, true), "старое название исчезло");
        assertTrue(found(t, "ARTIFACT", renamed, true));

        artifacts.delete(a.getId(), product);
        assertEquals(0, group(t, "ARTIFACT", true).total(), "удаленный материал пропал из поиска");
    }

    @Test
    void fullReindexRestoresIndex() {
        String t = token();
        String title = "Для переиндексации " + t;
        artifact(title, "Даташит");
        int count = indexer.reindexAll();
        assertTrue(count > 300, "в индексе SKU и материалы: " + count);
        assertTrue(found(t, "ARTIFACT", title, true));
        assertTrue(found("wep550k", "SKU", "WEP-550K", true));
    }

    @Test
    void skuTagsAndSpecsAreSearchable() {
        // Характеристики и теги входят в текст SKU: "Wi-Fi 6" есть в наименованиях и характеристиках точек доступа
        assertTrue(group("точка доступа wi-fi", "SKU", true).total() > 0);
    }

    // ----------------------------------------------------------- журнал

    @Test
    void queriesAndClicksAreLogged() {
        long before = jdbc.queryForObject("select count(*) from search_log", Long.class);
        String t = token();
        searchLog.record("PAGE", "product", "запрос " + t, 3L, 12, null, null);
        searchLog.record("CLICK", "product", "запрос " + t, null, 0, "SKU", 1L);
        searchLog.drain();
        assertEquals(before + 2, jdbc.queryForObject("select count(*) from search_log", Long.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from search_log where kind = 'CLICK' and query_text = ?", Long.class,
                "запрос " + t));
        assertNotNull(ModelNormalizer.normalize("x"));
    }
}
