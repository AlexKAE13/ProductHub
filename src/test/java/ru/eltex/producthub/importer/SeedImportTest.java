package ru.eltex.producthub.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import ru.eltex.producthub.common.AuditLogRepository;
import ru.eltex.producthub.sku.domain.LifecycleStatus;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.repo.SkuRepository;
import ru.eltex.producthub.sku.service.ModelNormalizer;

/** Импорт на реальных файлах из seed/ (SPEC.md 8.4). Исходные файлы не изменяются. */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
class SeedImportTest {

    @Autowired
    SeedImportService importer;
    @Autowired
    SkuRepository skus;
    @Autowired
    AuditLogRepository audit;

    private Sku sku(String model) {
        return skus.findCardByNormalized(ModelNormalizer.normalize(model)).orElseThrow();
    }

    @Test
    void importsAllSkusFromSiteCatalogAndRegistry() {
        importer.execute("test");
        // 304 уникальных артикула в каталоге сайтов + 5 SKU только из реестра ЖЦ
        assertTrue(skus.countByDeletedFalse() >= 309, "SKU в базе: " + skus.countByDeletedFalse());
    }

    @Test
    void lifecycleFieldsComeFromRegistry() {
        importer.execute("test");
        Sku s = sku("WEP-550K");
        assertEquals(LifecycleStatus.NEW, s.getLifecycleStatus());
        assertEquals("WEP", s.getLine().getCode());
        assertEquals("Wi-Fi", s.getLine().getFamily());
        assertEquals("Mediatek", s.getChip());
        assertEquals("2Q26", s.getGaDate());
        assertNull(s.getEolAnnounceDate(), "значение \"-\" считается пустым");
        assertEquals(LocalDate.of(2026, 7, 16), s.getLastReviewDate());
        assertEquals(LocalDate.of(2026, 7, 16).plusDays(90), s.getNextReviewDate());
        assertEquals("kuznetsov", s.getOwner().getLogin());
        assertEquals("Точка доступа внутренняя", s.getCategory());
        // данные каталога сайтов
        assertEquals("Беспроводной доступ", s.getSiteSectionRu());
        assertEquals("Новинка", s.getSiteStatusRu());
    }

    @Test
    void skuMissingOnSitesIsCreatedFromRegistry() {
        importer.execute("test");
        Sku s = sku("WEP-500K");
        assertEquals(LifecycleStatus.DEVELOPMENT, s.getLifecycleStatus());
        assertNull(s.getSiteSectionRu());
    }

    @Test
    void registryMatchesSiteSpellingIgnoringCase() {
        importer.execute("test");
        // в реестре "Sync", на сайте "SYNC": это один SKU
        Sku s = sku("WOP-2ac-LR5 Sync");
        assertEquals("WOP-2ac-LR5 SYNC", s.getModel());
        assertEquals("LR", s.getLine().getCode());
        assertEquals("Беспроводной мост", s.getCategory());
    }

    @Test
    void successorsAreLinked() {
        importer.execute("test");
        // преемник WEP-30L или другого устаревшего SKU реестра хотя бы у одного SKU найден
        long linked = skus.findAll().stream().filter(s -> s.getSuccessor() != null).count();
        assertTrue(linked > 0);
    }

    @Test
    void skusWithSlashInModelAreImported() {
        importer.execute("test");
        assertEquals("PM300T-220/12", sku("PM300T-220/12").getModel());
    }

    @Test
    void secondRunIsIdempotent() {
        importer.execute("test");
        long before = skus.count();
        long auditBefore = audit.count();
        ImportRun second = importer.execute("test");
        assertEquals(before, skus.count());
        assertEquals(0, second.getCreated());
        assertEquals(0, second.getUpdated());
        assertEquals(auditBefore + 0, audit.count());
    }

    @Test
    void problemsAreReportedInLogButDoNotStopImport() {
        ImportRun run = importer.execute("test");
        assertNotNull(run.getLog());
        assertTrue(run.getLog().contains("дубль артикула"), "дубли должны попасть в журнал");
        assertTrue(run.getLog().contains("WB-5P-PTP(?)"), "неверный преемник должен попасть в журнал");
        assertEquals(0, run.getErrors());
    }

    @Test
    void seedFilesAreNotModified() throws Exception {
        Path file = Path.of("seed/site_catalog_ru_en.xlsx");
        long size = Files.size(file);
        var time = Files.getLastModifiedTime(file);
        importer.execute("test");
        assertEquals(size, Files.size(file));
        assertEquals(time, Files.getLastModifiedTime(file));
    }
}
