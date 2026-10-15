package ru.eltex.producthub.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.pdf;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.pptx;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.artifact.repo.ArtifactRepository;
import ru.eltex.producthub.artifact.service.ArtifactService;
import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;

/** Импорт папки: структура как в рабочей папке Battle Cards (pptx и pdf в разных подпапках, служебные файлы). */
@SpringBootTest(properties = "producthub.import.folder-roots=${java.io.tmpdir},/Users/aleksandr/Library/Mobile Documents/com~apple~CloudDocs/ELTEX/Battle Cards")
@ActiveProfiles({"dev", "test"})
class FolderImportTest {

    static final String REAL_FOLDER = "/Users/aleksandr/Library/Mobile Documents/com~apple~CloudDocs/ELTEX/Battle Cards";

    @Autowired
    FolderImportService importService;
    @Autowired
    SeedImportService seedImport;
    @Autowired
    HubUserDetailsService users;
    @Autowired
    ArtifactRepository artifacts;
    @Autowired
    ArtifactService artifactService;

    HubUserDetails product;
    HubUserDetails sales;

    @BeforeEach
    void setUp() {
        seedImport.execute("test");
        // тесты независимы: материалы предыдущих тестов считаются удаленными
        var all = artifacts.findAll();
        all.forEach(a -> a.setDeleted(true));
        artifacts.saveAll(all);
        product = users.loadUserByUsername("product");
        sales = users.loadUserByUsername("sales");
    }

    private static void write(Path file, byte[] content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    /** Папка как в жизни: RU/PDF, RU/PPTX, EN/PDF, EN/PPTX, служебные файлы и архив. */
    private Path makeFolder(Path root) throws IOException {
        for (String lang : new String[] {"RU", "EN"}) {
            for (String stem : new String[] {"WEP-550K_" + lang, "WLC_" + lang, "Eltex_vs_Huawei_Indoor_" + lang,
                    "Eltex vs UniFi Indoor_" + lang, "Something_else_" + lang}) {
                write(root.resolve(lang + "/PDF/" + stem + ".pdf"), pdf());
                write(root.resolve(lang + "/PPTX/" + stem + ".pptx"), pptx());
            }
        }
        write(root.resolve("EN/PPTX/~$WLC_EN.pptx"), "lock".getBytes(StandardCharsets.UTF_8));
        write(root.resolve(".DS_Store"), new byte[] {0, 1, 2});
        write(root.resolve("RU.zip"), new byte[] {'P', 'K', 3, 4});
        write(root.resolve("notes.exe"), pdf());
        write(root.resolve("fake_RU.pdf"), "не pdf".getBytes(StandardCharsets.UTF_8));
        return root;
    }

    private PlanRow row(ImportPlan plan, String titleStart, String language) {
        return plan.getRows().stream().filter(r -> r.getTitle().startsWith(titleStart) && r.getLanguage().equals(language))
                .findFirst().orElseThrow(() -> new AssertionError("нет строки " + titleStart + " " + language));
    }

    private List<RowEdit> keepAll(ImportPlan plan) {
        List<RowEdit> edits = new ArrayList<>();
        for (PlanRow r : plan.getRows()) {
            RowEdit e = new RowEdit();
            e.setInclude(r.isInclude());
            e.setTitle(r.getTitle());
            e.setType(r.getType());
            e.setLanguage(r.getLanguage());
            e.setStatus(r.getStatus());
            e.setSkus(r.getSkus());
            e.setLine(r.getLine());
            e.setTags(r.getTags());
            e.setContour(r.getContour());
            e.setOwnerId(r.getOwnerId());
            e.setDescription(r.getDescription());
            edits.add(e);
        }
        return edits;
    }

    // ----------------------------------------------------------- предпросмотр

    @Test
    void scanPairsPptxWithPdfAndProposesMetadata(@TempDir Path tmp) throws IOException {
        ImportPlan plan = importService.scanFolder(makeFolder(tmp).toString(), product);

        // 5 материалов на каждый язык, pptx + pdf склеены в один
        assertEquals(10, plan.getRows().size());
        PlanRow card = row(plan, "WEP-550K", "RU");
        assertEquals(2, card.getFiles().size());
        assertEquals("MAIN", card.getFiles().get(0).role().name());
        assertTrue(card.getFiles().get(0).fileName().endsWith(".pptx"), "основной файл - pptx");
        assertEquals("PREVIEW", card.getFiles().get(1).role().name());
        assertTrue(card.getFiles().get(1).fileName().endsWith(".pdf"));

        // тип, SKU и язык по имени
        assertEquals("Карточка продукта", card.getType());
        assertEquals("WEP-550K", card.getSkus());
        assertTrue(card.isSkuFound());
        assertEquals(PlanRow.Action.CREATE, card.getAction());

        // линейка целиком
        PlanRow wlc = row(plan, "WLC", "EN");
        assertEquals("WLC", wlc.getLine());
        assertEquals("", wlc.getSkus());

        // сравнение: конкурент и сегмент превращаются в теги
        PlanRow cmp = row(plan, "Eltex vs Huawei Indoor", "RU");
        assertEquals("Сравнение с конкурентами", cmp.getType());
        assertEquals("Huawei, Indoor", cmp.getTags());
        assertEquals("Ubiquiti, Indoor", row(plan, "Eltex vs UniFi Indoor", "EN").getTags(), "UniFi - синоним Ubiquiti");

        // нет SKU и линейки: тип "Прочее" и предупреждение
        PlanRow other = row(plan, "Something else", "RU");
        assertEquals("Прочее", other.getType());
        assertFalse(other.getWarnings().isEmpty());
    }

    @Test
    void broadbandMaterialGetsTopicTagInsteadOfWarning(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("FBWA_EN.pdf"), pdf());
        ImportPlan plan = importService.scanFolder(tmp.toString(), product);
        PlanRow r = plan.getRows().get(0);
        assertEquals("Карточка продукта", r.getType());
        assertEquals("БШПД", r.getTags());
        assertTrue(r.getWarnings().isEmpty() || r.getWarnings().stream().noneMatch(w -> w.contains("тег")));
    }

    @Test
    void scanReportsSkippedFilesWithReason(@TempDir Path tmp) throws IOException {
        ImportPlan plan = importService.scanFolder(makeFolder(tmp).toString(), product);
        List<String> skipped = plan.getSkipped().stream().map(s -> s.path() + ": " + s.reason()).toList();
        assertTrue(skipped.stream().anyMatch(s -> s.contains("~$WLC_EN.pptx") && s.contains("ignore")), skipped.toString());
        assertTrue(skipped.stream().anyMatch(s -> s.contains(".DS_Store")));
        assertTrue(skipped.stream().anyMatch(s -> s.contains("RU.zip")));
        assertTrue(skipped.stream().anyMatch(s -> s.contains("notes.exe") && s.contains("не разрешено")));
        assertTrue(skipped.stream().anyMatch(s -> s.contains("fake_RU.pdf") && s.contains("не похоже")));
    }

    @Test
    void scanRejectsFoldersOutsideAllowedRoots(@TempDir Path tmp) throws IOException {
        assertEquals("validation.path.notAllowed", assertThrows(FormValidationException.class,
                () -> importService.scanFolder("/etc", product)).getErrors().get("path"));
        // обход через ".." выходит за разрешенный корень
        String escape = tmp.resolve("..").resolve("..").resolve("..").resolve("..").resolve("etc").toString();
        assertThrows(FormValidationException.class, () -> importService.scanFolder(escape, product));
        // ссылка внутри разрешенной папки на чужую папку тоже не пускает
        Path link = tmp.resolve("ссылка");
        Files.createSymbolicLink(link, Path.of("/etc"));
        assertThrows(FormValidationException.class, () -> importService.scanFolder(link.toString(), product));
    }

    @Test
    void emptyRootsDisableFolderImport() {
        FolderImportPolicy off = new FolderImportPolicy(new ImportProperties("seed", true, "kuznetsov", "", "Прочее", "OTHER",
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of()));
        assertFalse(off.enabled());
        assertFalse(off.allows(Path.of("/")));
    }

    @Test
    void scanRejectsMissingFolderAndForeignRoles(@TempDir Path tmp) {
        assertTrue(assertThrows(FormValidationException.class,
                () -> importService.scanFolder(tmp.resolve("нет-такой").toString(), product)).getErrors().containsKey("path"));
        assertThrows(AccessDeniedException.class, () -> importService.scanFolder(tmp.toString(), sales));
    }

    // ----------------------------------------------------------- подтверждение, dry-run, идемпотентность

    @Test
    void dryRunWritesNothing(@TempDir Path tmp) throws IOException {
        ImportPlan plan = importService.scanFolder(makeFolder(tmp).toString(), product);
        long before = artifacts.count();
        ImportRun run = importService.confirm(plan.getToken(), keepAll(plan), true, product);
        assertEquals(before, artifacts.count());
        assertEquals(10, run.getCreated(), "в dry-run считается, сколько будет создано");
        assertTrue(run.getLog().startsWith("Проверка без записи"));
    }

    @Test
    void confirmCreatesArtifactsLinkedToSkuLineAndTags(@TempDir Path tmp) throws IOException {
        ImportPlan plan = importService.scanFolder(makeFolder(tmp).toString(), product);
        ImportRun run = importService.confirm(plan.getToken(), keepAll(plan), false, product);
        assertEquals(10, run.getCreated(), run.getLog());
        assertEquals(0, run.getErrors(), run.getLog());

        var created = artifacts.findBySourceKey("wep-550k|RU");
        assertEquals(1, created.size());
        var detail = artifactService.detail(created.get(0).getId(), product);
        assertEquals("WEP-550K", detail.skus().get(0).getModel());
        assertEquals(2, detail.currentFiles().size());
        assertEquals("Карточка продукта", detail.artifact().getType().getName());

        var cmp = artifacts.findBySourceKey("eltex_vs_huawei_indoor|EN").get(0);
        var tags = artifactService.detail(cmp.getId(), product).tags();
        assertTrue(tags.stream().anyMatch(t -> t.getValue().equals("Huawei")));
        assertTrue(tags.stream().anyMatch(t -> t.getValue().equals("Indoor")));

        var line = artifacts.findBySourceKey("wlc|EN").get(0);
        assertEquals("WLC", artifactService.detail(line.getId(), product).artifact().getLine().getCode());
    }

    @Test
    void reimportDoesNotCreateDuplicatesAndChangedFileBecomesNewVersion(@TempDir Path tmp) throws IOException {
        Path folder = makeFolder(tmp);
        ImportPlan first = importService.scanFolder(folder.toString(), product);
        importService.confirm(first.getToken(), keepAll(first), false, product);
        long count = artifacts.count();

        // тот же набор файлов: все строки - "уже есть"
        ImportPlan second = importService.scanFolder(folder.toString(), product);
        assertEquals(10, second.getSkipCount());
        assertEquals(0, second.getCreateCount());
        ImportRun run = importService.confirm(second.getToken(), keepAll(second), false, product);
        assertEquals(0, run.getCreated());
        assertEquals(count, artifacts.count());

        // файл изменился: тот же материал получает новую версию
        write(folder.resolve("RU/PPTX/WEP-550K_RU.pptx"), pptx());
        ImportPlan third = importService.scanFolder(folder.toString(), product);
        PlanRow changed = row(third, "WEP-550K", "RU");
        assertEquals(PlanRow.Action.NEW_VERSION, changed.getAction());
        assertEquals(9, third.getSkipCount());
        importService.confirm(third.getToken(), keepAll(third), false, product);
        Artifact a = artifacts.findBySourceKey("wep-550k|RU").get(0);
        assertEquals(2, artifactService.detail(a.getId(), product).versions().size());
        assertEquals(count, artifacts.count());
    }

    @Test
    void userEditsInPreviewAreApplied(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("Unknown_thing_RU.pdf"), pdf());
        ImportPlan plan = importService.scanFolder(tmp.toString(), product);
        List<RowEdit> edits = keepAll(plan);
        edits.get(0).setTitle("Мой материал");
        edits.get(0).setType("Гайд по подбору оборудования");
        edits.get(0).setSkus("wep 550k");
        edits.get(0).setTags("Mesh");
        ImportRun run = importService.confirm(plan.getToken(), edits, false, product);
        assertEquals(1, run.getCreated(), run.getLog());
        Artifact a = artifacts.findBySourceKey("unknown_thing|RU").get(0);
        var detail = artifactService.detail(a.getId(), product);
        assertEquals("Мой материал", detail.artifact().getTitle());
        assertEquals("Гайд по подбору оборудования", detail.artifact().getType().getName());
        assertEquals("WEP-550K", detail.skus().get(0).getModel());
        assertEquals("Mesh", detail.tags().get(0).getValue());
    }

    @Test
    void badRowIsReportedButOthersAreImported(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("Good_RU.pdf"), pdf());
        write(tmp.resolve("Bad_RU.pdf"), pdf());
        ImportPlan plan = importService.scanFolder(tmp.toString(), product);
        List<RowEdit> edits = keepAll(plan);
        edits.get(1).setSkus("НЕТ-ТАКОГО-SKU");
        ImportRun run = importService.confirm(plan.getToken(), edits, false, product);
        assertEquals(1, run.getCreated());
        assertEquals(1, run.getErrors());
        assertTrue(run.getLog().contains("ОШИБКА"));
    }

    @Test
    void unselectedRowsAreSkipped(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("One_RU.pdf"), pdf());
        write(tmp.resolve("Two_RU.pdf"), pdf());
        ImportPlan plan = importService.scanFolder(tmp.toString(), product);
        List<RowEdit> edits = keepAll(plan);
        edits.get(0).setInclude(false);
        ImportRun run = importService.confirm(plan.getToken(), edits, false, product);
        assertEquals(1, run.getCreated());
        assertEquals(1, run.getSkipped());
    }

    @Test
    void planBelongsToItsAuthor(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("Mine_RU.pdf"), pdf());
        ImportPlan plan = importService.scanFolder(tmp.toString(), product);
        HubUserDetails admin = users.loadUserByUsername("admin");
        assertThrows(AccessDeniedException.class, () -> importService.confirm(plan.getToken(), List.of(), false, admin));
    }

    @Test
    void expiredPlanIsReported() {
        assertTrue(assertThrows(FormValidationException.class,
                () -> importService.confirm("нет-такого", List.of(), false, product)).getErrors().containsKey("plan"));
    }

    // ----------------------------------------------------------- загрузка файлов перетаскиванием

    @Test
    void uploadedFilesGetMetadataFromNames() {
        List<org.springframework.web.multipart.MultipartFile> upload = List.of(
                new MockMultipartFile("files", "WEP-550K_EN.pdf", "application/pdf", pdf()),
                new MockMultipartFile("files", "WEP-550K_EN.pptx", "application/octet-stream", pptx()));
        ImportPlan plan = importService.stageUploads(upload, product);
        assertEquals(1, plan.getRows().size(), "pptx и pdf - один материал");
        PlanRow r = plan.getRows().get(0);
        assertEquals("EN", r.getLanguage());
        assertEquals("WEP-550K", r.getSkus());
        assertEquals("Карточка продукта", r.getType());
        ImportRun run = importService.confirm(plan.getToken(), keepAll(plan), false, product);
        assertEquals(1, run.getCreated(), run.getLog());
        assertFalse(Files.exists(plan.getStagingDir()), "временные файлы удаляются после импорта");
    }

    @Test
    void salesCannotUpload() {
        List<org.springframework.web.multipart.MultipartFile> upload = List.of(new MockMultipartFile("files", "a_RU.pdf", "application/pdf", pdf()));
        assertThrows(AccessDeniedException.class, () -> importService.stageUploads(upload, sales));
    }

    // ----------------------------------------------------------- реальная рабочая папка (если она есть на этом компьютере)

    @Test
    void realBattleCardsFolderScansWithPreview() {
        assumeTrue(Files.isDirectory(Path.of(REAL_FOLDER)), "реальной папки нет на этом компьютере");
        ImportPlan plan = importService.scanFolder(REAL_FOLDER, product);
        assertNotNull(plan.getToken());
        // 77 файлов: 36 pdf + 40 pptx + zip, минус служебные и дубли имен -> около 36 материалов
        assertTrue(plan.getRows().size() >= 30, "материалов в плане: " + plan.getRows().size());
        // у всех материалов с парой pptx+pdf pdf стал предпросмотром
        assertTrue(plan.getRows().stream().filter(r -> r.getFiles().size() == 2).count() >= 25);
        // сравнения распознаны вместе с конкурентами
        long comparisons = plan.getRows().stream().filter(r -> r.getType().equals("Сравнение с конкурентами")).count();
        assertEquals(12, comparisons, "6 конкурентов на 2 языка");
        assertTrue(plan.getRows().stream().filter(r -> r.getType().equals("Сравнение с конкурентами"))
                .allMatch(r -> r.getTags().contains(", ") && r.getWarnings().isEmpty()), "теги конкурента и сегмента найдены");
        // служебные файлы и архив пропущены
        assertTrue(plan.getSkipped().stream().anyMatch(s -> s.path().contains("~$")));
        assertTrue(plan.getSkipped().stream().anyMatch(s -> s.path().endsWith("RU.zip")));
        // проверка без записи на реальных данных
        ImportRun dry = importService.confirm(plan.getToken(), keepAll(plan), true, product);
        assertEquals(0, dry.getErrors(), dry.getLog());
    }
}
