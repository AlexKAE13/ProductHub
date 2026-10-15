package ru.eltex.producthub.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.file;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.pdf;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.png;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.pptx;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.unique;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.artifact.domain.ArtifactStatus;
import ru.eltex.producthub.artifact.domain.FileRole;
import ru.eltex.producthub.artifact.domain.Language;
import ru.eltex.producthub.artifact.event.ArtifactUpdated;
import ru.eltex.producthub.artifact.service.ArtifactDetail;
import ru.eltex.producthub.artifact.service.ArtifactDraft;
import ru.eltex.producthub.artifact.service.ArtifactForm;
import ru.eltex.producthub.artifact.service.ArtifactNotFoundException;
import ru.eltex.producthub.artifact.service.ArtifactService;
import ru.eltex.producthub.artifact.service.ArtifactTypeService;
import ru.eltex.producthub.common.AuditService;
import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.importer.SeedImportService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;
import ru.eltex.producthub.sku.domain.Contour;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.repo.SkuRepository;
import ru.eltex.producthub.sku.service.ModelNormalizer;
import ru.eltex.producthub.sku.service.SkuService;

@SpringBootTest
@ActiveProfiles({"dev", "test"})
@RecordApplicationEvents
class ArtifactServiceTest {

    @Autowired
    ArtifactService service;
    @Autowired
    AuditService audit;
    @Autowired
    ArtifactTypeService types;
    @Autowired
    HubUserDetailsService users;
    @Autowired
    SkuRepository skus;
    @Autowired
    SkuService skuService;
    @Autowired
    SeedImportService importer;
    @Autowired
    ApplicationEvents events;
    @Autowired
    ApplicationEventPublisher publisher;

    HubUserDetails product;
    HubUserDetails serviceCenter;
    HubUserDetails sales;
    HubUserDetails partner;

    @BeforeEach
    void setUp() {
        importer.execute("test");
        product = users.loadUserByUsername("product");
        serviceCenter = users.loadUserByUsername("service");
        sales = users.loadUserByUsername("sales");
        partner = users.loadUserByUsername("partner");
    }

    private Sku sku(String model) {
        return skus.findByModelNormalized(ModelNormalizer.normalize(model)).orElseThrow();
    }

    private ArtifactDraft draft(String title, String type, Language language, Sku... skuList) {
        return new ArtifactDraft(title, null, types.byName(type).orElseThrow(), language, ArtifactStatus.ACTUAL,
                skuList.length > 0 ? skuList[0].getOwner() : sku("WEP-550K").getOwner(), null, null, null,
                List.of(skuList), List.of(), null);
    }

    private Artifact create(String title, String type, Language language, Sku... skuList) {
        return service.create(draft(title, type, language, skuList), List.of(file(title + ".pdf", pdf())), null, product);
    }

    // ----------------------------------------------------------- типы из реестра

    @Test
    void typesAreImportedFromRegistry() {
        assertTrue(types.byName("Даташит").isPresent());
        assertTrue(types.byName("Battle card").isPresent());
        assertTrue(types.byName("Карточка продукта").isPresent(), "дополнительный тип из конфигурации");
        assertTrue(types.byName("Наличие").isEmpty(), "наличие - данные SKU, а не материал");
        assertEquals(Contour.EXTERNAL, types.byName("Видео").orElseThrow().getDefaultContour());
        assertEquals(Contour.INTERNAL, types.byName("Даташит").orElseThrow().getDefaultContour(),
                "\"Оба\" считается внутренним");
        assertEquals(5, types.byName("Даташит").orElseThrow().getImportance());
    }

    // ----------------------------------------------------------- создание и версии

    @Test
    void createStoresFileAsVersionOneAndWritesAudit() {
        Artifact a = create(unique("Тест"), "Даташит", Language.RU, sku("WEP-550K"));
        ArtifactDetail d = service.detail(a.getId(), product);
        assertEquals(1, d.versions().size());
        assertEquals(1, d.versions().get(0).getVersionNo());
        assertEquals(1, d.currentFiles().size());
        assertEquals(FileRole.MAIN, d.currentFiles().get(0).getRole());
        assertEquals(64, d.currentFiles().get(0).getSha256().length());
        assertTrue(d.history().stream().anyMatch(h -> h.getAction().name().equals("CREATE")));
        assertEquals("WEP-550K", d.skus().get(0).getModel());
    }

    @Test
    void newVersionGoesToHistoryOldStaysDownloadable() {
        Artifact a = create(unique("Версии"), "Даташит", Language.RU, sku("WEP-550K"));
        var firstFile = service.detail(a.getId(), product).currentFiles().get(0);

        var added = service.addVersion(a.getId(), List.of(file("v2.pdf", pdf())), "правки", product);
        assertEquals(2, added.versionNo());

        ArtifactDetail d = service.detail(a.getId(), product);
        assertEquals(2, d.versions().size());
        assertEquals(2, d.artifact().getCurrentVersion().getVersionNo());
        assertNotNull(service.fileForDownload(firstFile.getId(), product), "старая версия доступна для скачивания");
        assertTrue(d.history().stream().anyMatch(h -> "version".equals(h.getField())));
    }

    @Test
    void newVersionPublishesArtifactUpdatedAndRefreshesReview() {
        Artifact a = create(unique("Событие"), "Даташит", Language.RU, sku("WEP-550K"));
        long before = events.stream(ArtifactUpdated.class).count();
        service.addVersion(a.getId(), List.of(file("v2.pdf", pdf())), null, product);
        var published = events.stream(ArtifactUpdated.class).toList();
        assertEquals(before + 1, published.size());
        ArtifactUpdated last = published.get(published.size() - 1);
        assertEquals(a.getId(), last.artifactId());
        assertEquals(2, last.versionNo());
        assertFalse(last.created());
        assertFalse(service.detail(a.getId(), product).reviewDue());
    }

    @Test
    void newVersionMakesOutdatedArtifactActual() {
        Artifact a = create(unique("Устарел"), "Даташит", Language.RU, sku("WEP-550K"));
        ArtifactForm form = service.formFor(a.getId(), product);
        form.setStatus("OUTDATED");
        service.update(a.getId(), form, product);
        service.addVersion(a.getId(), List.of(file("new.pdf", pdf())), null, product);
        assertEquals(ArtifactStatus.ACTUAL, service.detail(a.getId(), product).artifact().getStatus());
    }

    @Test
    void pptxWithPdfPreviewAreStoredInOneVersion() {
        Artifact a = service.create(draft(unique("Пара"), "Презентация (продуктовая)", Language.EN, sku("WEP-550K")),
                List.of(file("x.pptx", pptx(), FileRole.MAIN), file("x.pdf", pdf(), FileRole.PREVIEW)), null, product);
        var files = service.detail(a.getId(), product).currentFiles();
        assertEquals(2, files.size());
        assertEquals(FileRole.MAIN, files.get(0).getRole());
        assertEquals("x.pptx", files.get(0).getFileName());
        assertEquals(FileRole.PREVIEW, files.get(1).getRole());
        assertEquals(service.detail(a.getId(), product).previewFile().getFileName(), "x.pdf");
    }

    @Test
    void linkOnlyArtifactNeedsNoFile() {
        Artifact a = service.create(new ArtifactDraft(unique("Видео"), null, types.byName("Видео").orElseThrow(),
                Language.EN, ArtifactStatus.ACTUAL, product.getId() == null ? null : sku("WEP-550K").getOwner(), null,
                "https://www.youtube.com/watch?v=abc", null, List.of(sku("WEP-550K")), List.of(), null),
                List.of(), null, product);
        assertEquals("https://www.youtube.com/watch?v=abc", a.getExternalUrl());
        assertTrue(service.detail(a.getId(), product).versions().isEmpty());
    }

    @Test
    void artifactWithoutFileAndLinkIsRejected() {
        var e = assertThrows(FormValidationException.class,
                () -> service.create(draft(unique("Пусто"), "Даташит", Language.RU), List.of(), null, product));
        assertTrue(e.getErrors().containsKey("files"));
    }

    // ----------------------------------------------------------- проверка файлов

    @Test
    void fileContentMustMatchExtension() {
        byte[] notPdf = "это обычный текст, а не pdf".getBytes(StandardCharsets.UTF_8);
        var e = assertThrows(FormValidationException.class, () -> service.create(
                draft(unique("Подмена"), "Даташит", Language.RU), List.of(file("fake.pdf", notPdf)), null, product));
        assertEquals("validation.file.content", e.getErrors().get("files"));
    }

    @Test
    void forbiddenExtensionIsRejected() {
        var e = assertThrows(FormValidationException.class, () -> service.create(
                draft(unique("Exe"), "Даташит", Language.RU), List.of(file("virus.exe", pdf())), null, product));
        assertEquals("validation.file.extension", e.getErrors().get("files"));
    }

    // ----------------------------------------------------------- права

    @Test
    void salesCannotUploadOrEdit() {
        assertThrows(AccessDeniedException.class, () -> service.create(
                draft(unique("Продажи"), "Даташит", Language.RU), List.of(file("a.pdf", pdf())), null, sales));
        Artifact a = create(unique("Чужой"), "Даташит", Language.RU, sku("WEP-550K"));
        assertThrows(AccessDeniedException.class, () -> service.confirmActual(a.getId(), sales));
        assertThrows(AccessDeniedException.class, () -> service.delete(a.getId(), sales));
        assertThrows(AccessDeniedException.class, () -> service.addVersion(a.getId(), List.of(file("b.pdf", pdf())), null, sales));
    }

    @Test
    void serviceCenterCanUploadButNotBulk() {
        Artifact a = service.create(draft(unique("СЦ"), "Руководство по эксплуатации / настройке", Language.RU, sku("WEP-550K")),
                List.of(file("manual.pdf", pdf())), null, serviceCenter);
        assertNotNull(a.getId());
        assertThrows(AccessDeniedException.class,
                () -> service.bulk(List.of(a.getId()), ArtifactService.BulkAction.STATUS, "OUTDATED", serviceCenter));
    }

    @Test
    void partnerDoesNotSeeInternalArtifactsOrFiles() {
        Artifact internal = create(unique("Внутренний"), "Даташит", Language.RU, sku("WEP-550K"));
        var fileId = service.detail(internal.getId(), product).currentFiles().get(0).getId();
        assertThrows(AccessDeniedException.class, () -> service.detail(internal.getId(), partner));
        assertThrows(AccessDeniedException.class, () -> service.fileForDownload(fileId, partner));

        ArtifactForm form = service.formFor(internal.getId(), product);
        form.setContour("EXTERNAL");
        service.update(internal.getId(), form, product);
        assertNotNull(service.detail(internal.getId(), partner));
        assertTrue(service.detail(internal.getId(), partner).history().isEmpty(), "история только для внутренних");
    }

    // ----------------------------------------------------------- массовые действия, подтверждение, удаление

    @Test
    void bulkChangesStatusAddsTagAndBindsSku() {
        Artifact a1 = create(unique("М1"), "Даташит", Language.RU);
        Artifact a2 = create(unique("М2"), "Даташит", Language.EN);
        service.bulk(List.of(a1.getId(), a2.getId()), ArtifactService.BulkAction.STATUS, "OUTDATED", product);
        assertEquals(ArtifactStatus.OUTDATED, service.detail(a1.getId(), product).artifact().getStatus());
        assertEquals(ArtifactStatus.OUTDATED, service.detail(a2.getId(), product).artifact().getStatus());

        service.bulk(List.of(a1.getId(), a2.getId()), ArtifactService.BulkAction.ADD_SKU, "wep550k", product);
        assertEquals("WEP-550K", service.detail(a1.getId(), product).skus().get(0).getModel());

        var e = assertThrows(FormValidationException.class,
                () -> service.bulk(List.of(a1.getId()), ArtifactService.BulkAction.ADD_SKU, "НЕТ-ТАКОГО", product));
        assertTrue(e.getErrors().containsKey("bulk"));
    }

    @Test
    void bulkDeleteHidesArtifactsKeepsAuditAndIsOnlyForManagers() {
        Artifact a1 = create(unique("Уд1"), "Даташит", Language.RU);
        Artifact a2 = create(unique("Уд2"), "Даташит", Language.EN);
        Artifact keep = create(unique("Остается"), "Даташит", Language.RU);

        assertThrows(AccessDeniedException.class,
                () -> service.bulk(List.of(a1.getId()), ArtifactService.BulkAction.DELETE, "1", serviceCenter));
        assertEquals(2, service.bulk(List.of(a1.getId(), a2.getId()), ArtifactService.BulkAction.DELETE, "1", product));

        assertThrows(ArtifactNotFoundException.class, () -> service.detail(a1.getId(), product));
        assertThrows(ArtifactNotFoundException.class, () -> service.detail(a2.getId(), product));
        assertEquals(keep.getId(), service.detail(keep.getId(), product).artifact().getId(), "остальные не затронуты");
        assertEquals("DELETE", audit.history(ArtifactService.ENTITY, a1.getId(), 3).get(0).getAction().name());
    }

    @Test
    void confirmActualMovesReviewDateWithoutNewVersion() {
        Artifact a = create(unique("Ревью"), "Даташит", Language.RU);
        ArtifactForm form = service.formFor(a.getId(), product);
        form.setReviewPeriodDays("1");
        service.update(a.getId(), form, product);
        service.confirmActual(a.getId(), product);
        ArtifactDetail d = service.detail(a.getId(), product);
        assertEquals(1, d.versions().size());
        assertTrue(d.artifact().getReviewDueAt().isAfter(LocalDateTime.now()));
        assertTrue(d.history().stream().anyMatch(h -> "lastReviewedAt".equals(h.getField())));
    }

    @Test
    void softDeleteHidesArtifact() {
        Artifact a = create(unique("Удалить"), "Даташит", Language.RU);
        service.delete(a.getId(), product);
        assertThrows(RuntimeException.class, () -> service.detail(a.getId(), product));
    }

    @Test
    void updateWritesChangesToAuditAndRejectsUnknownSku() {
        Artifact a = create(unique("Правка"), "Даташит", Language.RU);
        ArtifactForm form = service.formFor(a.getId(), product);
        form.setTitle("Новое название");
        form.setLanguage("EN");
        service.update(a.getId(), form, product);
        var history = service.detail(a.getId(), product).history();
        assertTrue(history.stream().anyMatch(h -> "title".equals(h.getField()) && "Новое название".equals(h.getNewValue())));
        assertTrue(history.stream().anyMatch(h -> "language".equals(h.getField())));

        form.setSkus("НЕТ-ТАКОГО");
        assertTrue(assertThrows(FormValidationException.class, () -> service.update(a.getId(), form, product))
                .getErrors().containsKey("skus"));
    }

    // ----------------------------------------------------------- дубли

    @Test
    void sameFileUploadedTwiceIsFoundAsDuplicate() {
        byte[] content = pdf();
        Artifact a = service.create(draft(unique("Дубль"), "Даташит", Language.RU), List.of(file("d.pdf", content)), null,
                product);
        String sha = service.detail(a.getId(), product).currentFiles().get(0).getSha256();
        assertEquals(1, service.findDuplicates(sha).size());

        Artifact b = service.create(draft(unique("Дубль2"), "Даташит", Language.RU), List.of(file("d2.pdf", content)), null,
                product);
        assertEquals(2, service.findDuplicates(sha).size());
        var added = service.addVersion(b.getId(), List.of(file("d3.pdf", content)), null, product);
        assertFalse(added.duplicateWarnings().isEmpty(), "при загрузке дубля показывается предупреждение");
    }

    // ----------------------------------------------------------- заполненность SKU

    @Test
    void completenessGrowsWithDatasheetsAndPhoto() {
        Sku s = skuService.findIdByModel("WEP-500K") == null ? null : sku("WEP-500K");
        int before = skus.findById(s.getId()).orElseThrow().getCompleteness();
        create(unique("DS-RU"), "Даташит", Language.RU, s);
        int withRu = skus.findById(s.getId()).orElseThrow().getCompleteness();
        assertTrue(withRu > before, withRu + " > " + before);
        create(unique("DS-EN"), "Даташит", Language.EN, s);
        int withBoth = skus.findById(s.getId()).orElseThrow().getCompleteness();
        assertTrue(withBoth > withRu, withBoth + " > " + withRu);
        service.create(draft(unique("Фото"), "Фото", Language.NONE, s), List.of(file("p.png", png())), null, product);
        int withPhoto = skus.findById(s.getId()).orElseThrow().getCompleteness();
        assertTrue(withPhoto > withBoth, withPhoto + " > " + withBoth);
    }

    @Test
    void outdatedDatasheetDoesNotCountForCompleteness() {
        Sku s = sku("WEP-50L");
        Artifact ru = create(unique("Устар-RU"), "Даташит", Language.RU, s);
        int actual = skus.findById(s.getId()).orElseThrow().getCompleteness();
        service.bulk(List.of(ru.getId()), ArtifactService.BulkAction.STATUS, "OUTDATED", product);
        int outdated = skus.findById(s.getId()).orElseThrow().getCompleteness();
        assertTrue(outdated < actual, outdated + " < " + actual);
    }
}
