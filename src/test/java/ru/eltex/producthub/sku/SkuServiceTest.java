package ru.eltex.producthub.sku;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import ru.eltex.producthub.common.AuditAction;
import ru.eltex.producthub.common.AuditLog;
import ru.eltex.producthub.common.AuditService;
import ru.eltex.producthub.importer.SeedImportService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.domain.SpecDefinition;
import ru.eltex.producthub.sku.service.CardSection;
import ru.eltex.producthub.sku.service.ReferenceDataService;
import ru.eltex.producthub.sku.service.SkuBlock;
import ru.eltex.producthub.sku.service.SkuCard;
import ru.eltex.producthub.sku.service.SkuForm;
import ru.eltex.producthub.sku.service.SkuService;
import ru.eltex.producthub.sku.service.SkuValidationException;

@SpringBootTest
@ActiveProfiles({"dev", "test"})
class SkuServiceTest {

    @Autowired
    SkuService service;
    @Autowired
    HubUserDetailsService userService;
    @Autowired
    ReferenceDataService reference;
    @Autowired
    AuditService audit;
    @Autowired
    SeedImportService importer;
    @Autowired
    EntityManagerFactory emf;

    HubUserDetails product;
    HubUserDetails service_;
    HubUserDetails sales;
    HubUserDetails partner;
    HubUserDetails admin;

    @BeforeEach
    void setUp() {
        importer.execute("test");
        product = userService.loadUserByUsername("product");
        service_ = userService.loadUserByUsername("service");
        sales = userService.loadUserByUsername("sales");
        partner = userService.loadUserByUsername("partner");
        admin = userService.loadUserByUsername("admin");
    }

    private SkuForm newForm(String model) {
        SkuForm f = new SkuForm();
        f.setModel(model);
        f.setLineId(reference.lines().stream().filter(l -> l.getCode().equals("WEP")).findFirst().orElseThrow().getId());
        f.setCategory("Точка доступа внутренняя");
        f.setOwnerId(product.getId());
        f.setLifecycleStatus("DEVELOPMENT");
        return f;
    }

    private String unique() {
        return "TST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Sku create(String model) {
        return service.create(newForm(model), product);
    }

    // ----------------------------------------------------------- права

    @Test
    void salesAndHeadCannotCreateOrEdit() {
        assertThrows(AccessDeniedException.class, () -> service.create(newForm(unique()), sales));
        Sku s = create(unique());
        assertThrows(AccessDeniedException.class, () -> service.update(s.getId(), SkuBlock.TEXT, new SkuForm(), sales));
        assertThrows(AccessDeniedException.class, () -> service.markReviewed(s.getId(), sales));
        assertThrows(AccessDeniedException.class, () -> service.delete(s.getId(), sales));
    }

    @Test
    void serviceCenterCannotEditNonTechnicalBlocks() {
        Sku s = create(unique());
        assertThrows(AccessDeniedException.class, () -> service.create(newForm(unique()), service_));
        assertThrows(AccessDeniedException.class, () -> service.update(s.getId(), SkuBlock.LIFECYCLE, new SkuForm(), service_));
        assertThrows(AccessDeniedException.class, () -> service.update(s.getId(), SkuBlock.COMMERCIAL, new SkuForm(), service_));
    }

    @Test
    void serviceCenterEditsOnlyTechnicalSpecGroups() {
        Sku s = create(unique());
        List<SpecDefinition> template = reference.specDefinitionsFor(s.getCategory());
        SpecDefinition technical = template.stream().filter(d -> d.getKey().equals("weight")).findFirst().orElseThrow();
        SpecDefinition certificates = template.stream().filter(d -> d.getKey().equals("certificates")).findFirst().orElseThrow();

        SkuForm form = new SkuForm();
        form.getSpecs().put(technical.getId(), "0.5");
        form.getSpecs().put(certificates.getId(), "EAC");
        service.update(s.getId(), SkuBlock.SPECS, form, service_);

        SkuForm saved = service.formFor(s.getId(), product);
        assertEquals("0.5", saved.getSpecs().get(technical.getId()));
        assertNull(saved.getSpecs().get(certificates.getId()), "сертификаты правит только PRODUCT");
        assertFalse(service.editableSpecs(s.getId(), service_).stream().anyMatch(d -> d.getKey().equals("certificates")));
    }

    @Test
    void partnerDoesNotSeeInternalSkuOrSections() {
        Sku s = create(unique());
        assertThrows(AccessDeniedException.class, () -> service.loadCard(s.getModel(), partner));

        SkuForm main = service.formFor(s.getId(), product);
        main.setContour("EXTERNAL");
        service.update(s.getId(), SkuBlock.MAIN, main, product);

        SkuCard card = service.loadCard(s.getModel(), partner);
        assertTrue(card.can(CardSection.HEADER));
        assertFalse(card.can(CardSection.COMMERCIAL), "цены партнеру не показываются");
        assertFalse(card.can(CardSection.PLANS), "планы по продукту только во внутреннем контуре");
        assertFalse(card.can(CardSection.HISTORY));
    }

    @Test
    void onlyFilledSpecsAreShownAndEmptyOnesAreCountedForEditors() {
        Sku s = create(unique());
        SkuCard forProduct = service.loadCard(s.getModel(), product);
        SkuCard forSales = service.loadCard(s.getModel(), sales);
        SkuCard forService = service.loadCard(s.getModel(), service_);
        assertTrue(forProduct.specGroups().isEmpty(), "пустые характеристики не выводятся списком");
        assertEquals(reference.specDefinitionsFor(s.getCategory()).size(), forProduct.emptySpecs(),
                "продакт видит число всех незаполненных полей");
        assertEquals(0, forSales.emptySpecs(), "остальным о пустых полях не сообщается");
        assertTrue(forService.emptySpecs() > 0 && forService.emptySpecs() < forProduct.emptySpecs(),
                "сервисный центр видит только технические поля");

        SpecDefinition weight = reference.specDefinitionsFor(s.getCategory()).stream()
                .filter(d -> d.getKey().equals("weight")).findFirst().orElseThrow();
        SkuForm form = new SkuForm();
        form.getSpecs().put(weight.getId(), "0.4");
        service.update(s.getId(), SkuBlock.SPECS, form, product);
        SkuCard after = service.loadCard(s.getModel(), sales);
        assertEquals(1, after.specGroups().stream().mapToInt(g -> g.rows().size()).sum());
        assertEquals("Конструктив", after.specGroups().get(0).name());
    }

    // ----------------------------------------------------------- создание и копирование

    @Test
    void duplicateModelIsRejectedEvenWithDifferentSpelling() {
        String model = unique();
        create(model);
        SkuValidationException e = assertThrows(SkuValidationException.class,
                () -> service.create(newForm(model.toLowerCase().replace("-", " ")), product));
        assertTrue(e.getErrors().containsKey("model"));
    }

    @Test
    void requiredFieldsAreChecked() {
        SkuValidationException e = assertThrows(SkuValidationException.class, () -> service.create(new SkuForm(), product));
        assertTrue(e.getErrors().keySet().containsAll(List.of("model", "lineId", "category", "lifecycleStatus", "ownerId")));
    }

    @Test
    void copyKeepsSpecsAndDescriptionButHasNewModel() {
        Sku source = create(unique());
        SpecDefinition weight = reference.specDefinitionsFor(source.getCategory()).stream()
                .filter(d -> d.getKey().equals("weight")).findFirst().orElseThrow();
        SkuForm edit = new SkuForm();
        edit.getSpecs().put(weight.getId(), "1.2");
        service.update(source.getId(), SkuBlock.SPECS, edit, product);
        SkuForm text = new SkuForm();
        text.setDescription("Описание образца");
        service.update(source.getId(), SkuBlock.TEXT, text, product);

        SkuForm copyForm = newForm(unique());
        copyForm.setCopyFromId(source.getId());
        Sku copy = service.create(copyForm, product);

        SkuForm saved = service.formFor(copy.getId(), product);
        assertEquals("1.2", saved.getSpecs().get(weight.getId()));
        assertEquals("Описание образца", saved.getDescription());
        assertTrue(audit.history(SkuService.ENTITY, copy.getId(), 5).get(0).getNote().startsWith("копия"));
    }

    // ----------------------------------------------------------- аудит

    @Test
    void everyChangeIsWrittenToAuditWithOldAndNewValue() {
        Sku s = create(unique());
        SkuForm form = service.formFor(s.getId(), product);
        form.setLifecycleStatus("PRODUCTION");
        form.setGaDate("3Q26");
        service.update(s.getId(), SkuBlock.LIFECYCLE, form, product);

        List<AuditLog> history = audit.history(SkuService.ENTITY, s.getId(), 20);
        AuditLog status = history.stream().filter(h -> "lifecycleStatus".equals(h.getField())).findFirst().orElseThrow();
        assertEquals(AuditAction.STATUS_CHANGE, status.getAction());
        assertEquals("DEVELOPMENT", status.getOldValue());
        assertEquals("PRODUCTION", status.getNewValue());
        AuditLog ga = history.stream().filter(h -> "gaDate".equals(h.getField())).findFirst().orElseThrow();
        assertEquals("3Q26", ga.getNewValue());
        assertEquals("product", ga.getUserLogin());
        assertTrue(history.stream().anyMatch(h -> h.getAction() == AuditAction.CREATE));
    }

    @Test
    void unchangedSaveWritesNothing() {
        Sku s = create(unique());
        int before = audit.history(SkuService.ENTITY, s.getId(), 50).size();
        service.update(s.getId(), SkuBlock.MAIN, service.formFor(s.getId(), product), product);
        assertEquals(before, audit.history(SkuService.ENTITY, s.getId(), 50).size());
    }

    @Test
    void foreignSkuEditIsMarkedInAudit() {
        // product (тестовый) не владелец SKU, созданных под kuznetsov
        Sku s = create(unique());
        SkuForm form = service.formFor(s.getId(), product);
        form.setOwnerId(userService.loadUserByUsername("kuznetsov").getId());
        form.getOwnerIds().clear();
        service.update(s.getId(), SkuBlock.MAIN, form, product);
        SkuForm text = new SkuForm();
        text.setDescription("правка чужого SKU");
        service.update(s.getId(), SkuBlock.TEXT, text, product);
        AuditLog last = audit.history(SkuService.ENTITY, s.getId(), 1).get(0);
        assertEquals("description", last.getField());
        assertEquals("чужой SKU", last.getNote());

        // админ не помечается
        text.setDescription("правка админа");
        service.update(s.getId(), SkuBlock.TEXT, text, admin);
        assertNull(audit.history(SkuService.ENTITY, s.getId(), 1).get(0).getNote());
    }

    // ----------------------------------------------------------- ревью, удаление, проверка данных

    @Test
    void reviewDoneSetsDatesToTodayAndPlus90() {
        Sku s = create(unique());
        service.markReviewed(s.getId(), product);
        SkuForm form = service.formFor(s.getId(), product);
        assertEquals(LocalDate.now().toString(), form.getLastReviewDate());
        assertEquals(LocalDate.now().plusDays(90).toString(), form.getNextReviewDate());
    }

    @Test
    void softDeleteHidesSkuButKeepsRow() {
        Sku s = create(unique());
        service.delete(s.getId(), product);
        assertEquals(null, service.findIdByModel(s.getModel()));
        assertTrue(audit.history(SkuService.ENTITY, s.getId(), 5).stream().anyMatch(h -> h.getAction() == AuditAction.DELETE));
    }

    @Test
    void invalidLifecycleDateAndNumbersAreRejected() {
        Sku s = create(unique());
        SkuForm form = service.formFor(s.getId(), product);
        form.setGaDate("скоро");
        assertTrue(assertThrows(SkuValidationException.class,
                () -> service.update(s.getId(), SkuBlock.LIFECYCLE, form, product)).getErrors().containsKey("gaDate"));

        SkuForm commercial = new SkuForm();
        commercial.setPrice("abc");
        assertTrue(assertThrows(SkuValidationException.class,
                () -> service.update(s.getId(), SkuBlock.COMMERCIAL, commercial, product)).getErrors().containsKey("price"));
    }

    @Test
    void successorMustExist() {
        Sku s = create(unique());
        SkuForm form = service.formFor(s.getId(), product);
        form.setSuccessorModel("НЕТ-ТАКОГО");
        assertTrue(assertThrows(SkuValidationException.class,
                () -> service.update(s.getId(), SkuBlock.LIFECYCLE, form, product)).getErrors().containsKey("successorModel"));
        form.setSuccessorModel("wep550k"); // другое написание существующего SKU
        service.update(s.getId(), SkuBlock.LIFECYCLE, form, product);
        assertEquals("WEP-550K", service.loadCard(s.getModel(), product).sku().getSuccessor().getModel());
    }

    // ----------------------------------------------------------- коммерческий блок и заполненность

    @Test
    void commercialBlockWarnsWhenStaleAndFreshAfterSave() {
        Sku s = create(unique());
        assertTrue(service.loadCard(s.getModel(), product).commercialStale());
        SkuForm commercial = new SkuForm();
        commercial.setPrice("1 500,50");
        commercial.setCurrency("rub");
        commercial.setAvailability("IN_STOCK");
        commercial.setPriceList("INTERNAL");
        service.update(s.getId(), SkuBlock.COMMERCIAL, commercial, product);
        SkuCard card = service.loadCard(s.getModel(), product);
        assertFalse(card.commercialStale());
        assertEquals("RUB", card.sku().getCurrency());
        assertEquals(0, new java.math.BigDecimal("1500.50").compareTo(card.sku().getPrice()));
        assertEquals("product", card.sku().getCommercialUpdatedBy());
    }

    @Test
    void completenessGrowsWhenFieldsAreFilled() {
        Sku s = create(unique());
        int empty = service.loadCard(s.getModel(), product).sku().getCompleteness();

        SkuForm main = service.formFor(s.getId(), product);
        main.setNameRu("Тестовая точка");
        main.setNameEn("Test AP");
        main.setChip("Mediatek");
        main.setSiteSectionRu("Беспроводной доступ");
        service.update(s.getId(), SkuBlock.MAIN, main, product);
        int withIdentity = service.loadCard(s.getModel(), product).sku().getCompleteness();
        assertTrue(withIdentity > empty, withIdentity + " > " + empty);

        SkuForm specs = new SkuForm();
        reference.specDefinitionsFor(s.getCategory()).forEach(d -> {
            if (d.getValueType().name().equals("TEXT")) {
                specs.getSpecs().put(d.getId(), "x");
            }
        });
        service.update(s.getId(), SkuBlock.SPECS, specs, product);
        int withSpecs = service.loadCard(s.getModel(), product).sku().getCompleteness();
        assertTrue(withSpecs > withIdentity, withSpecs + " > " + withIdentity);
        assertTrue(withSpecs <= 100);
    }

    // ----------------------------------------------------------- скорость: нет N+1

    @Test
    void cardIsLoadedWithFewQueries() {
        service.loadCard("WEP-550K", product); // прогрев кэша справочников
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        SkuCard card = service.loadCard("WEP-550K", product);
        assertNotNull(card);
        long queries = stats.getPrepareStatementCount();
        assertTrue(queries <= 7, "запросов на карточку: " + queries);

        // и при большом числе характеристик и истории число запросов не растет
        Sku s = create(unique());
        reference.specDefinitionsFor(s.getCategory());
        SkuForm specs = new SkuForm();
        reference.specDefinitionsFor(s.getCategory()).forEach(d -> {
            if (d.getValueType().name().equals("TEXT")) {
                specs.getSpecs().put(d.getId(), "v");
            }
        });
        service.update(s.getId(), SkuBlock.SPECS, specs, product);
        service.loadCard(s.getModel(), product);
        stats.clear();
        service.loadCard(s.getModel(), product);
        assertTrue(stats.getPrepareStatementCount() <= 7, "запросов: " + stats.getPrepareStatementCount());
    }
}
