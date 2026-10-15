package ru.eltex.producthub.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.file;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.pdf;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.artifact.domain.ArtifactStatus;
import ru.eltex.producthub.artifact.domain.Language;
import ru.eltex.producthub.artifact.repo.ArtifactRepository;
import ru.eltex.producthub.artifact.service.ArtifactDraft;
import ru.eltex.producthub.artifact.service.ArtifactService;
import ru.eltex.producthub.artifact.service.ArtifactStalenessJob;
import ru.eltex.producthub.artifact.service.ArtifactTypeService;
import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.firmware.domain.FirmwareStatus;
import ru.eltex.producthub.firmware.event.FirmwarePublished;
import ru.eltex.producthub.firmware.service.FirmwareForm;
import ru.eltex.producthub.firmware.service.FirmwareService;
import ru.eltex.producthub.importer.SeedImportService;
import ru.eltex.producthub.qa.domain.QuestionKind;
import ru.eltex.producthub.qa.domain.TargetType;
import ru.eltex.producthub.qa.service.QuestionService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.repo.SkuRepository;
import ru.eltex.producthub.sku.service.ModelNormalizer;
import ru.eltex.producthub.sku.service.SkuBlock;
import ru.eltex.producthub.sku.service.SkuForm;
import ru.eltex.producthub.sku.service.SkuReviewJob;
import ru.eltex.producthub.sku.service.SkuService;
import ru.eltex.producthub.task.domain.RelatedType;
import ru.eltex.producthub.task.domain.Task;
import ru.eltex.producthub.task.domain.TaskStatus;
import ru.eltex.producthub.task.domain.TaskType;
import ru.eltex.producthub.task.service.TaskFilter;
import ru.eltex.producthub.task.service.TaskForm;
import ru.eltex.producthub.task.service.TaskRuleException;
import ru.eltex.producthub.task.service.TaskService;

@SpringBootTest
@ActiveProfiles({"dev", "test"})
class TaskTest {

    @Autowired
    TaskService tasks;
    @Autowired
    QuestionService questions;
    @Autowired
    FirmwareService firmware;
    @Autowired
    SkuService skuService;
    @Autowired
    SkuRepository skus;
    @Autowired
    ArtifactService artifactService;
    @Autowired
    ArtifactRepository artifactRepository;
    @Autowired
    ArtifactTypeService types;
    @Autowired
    ArtifactStalenessJob stalenessJob;
    @Autowired
    SkuReviewJob reviewJob;
    @Autowired
    HubUserDetailsService users;
    @Autowired
    SeedImportService importer;
    @Autowired
    ApplicationEventPublisher publisher;

    HubUserDetails product;
    HubUserDetails admin;
    HubUserDetails head;
    HubUserDetails sales;
    HubUserDetails serviceCenter;
    HubUserDetails kuznetsov;

    @BeforeEach
    void setUp() {
        importer.execute("test");
        product = users.loadUserByUsername("product");
        admin = users.loadUserByUsername("admin");
        head = users.loadUserByUsername("head");
        sales = users.loadUserByUsername("sales");
        serviceCenter = users.loadUserByUsername("service");
        kuznetsov = users.loadUserByUsername("kuznetsov");
    }

    private static String marker() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private List<Task> all() {
        return tasks.board(new TaskFilter("all", null, null, null), product).columns().values().stream()
                .flatMap(List::stream).collect(Collectors.toList());
    }

    private List<Task> withTitle(String part) {
        return all().stream().filter(t -> t.getTitle().contains(part)).toList();
    }

    private Sku sku(String model) {
        return skus.findByModelNormalized(ModelNormalizer.normalize(model)).orElseThrow();
    }

    // ----------------------------------------------------------- триггер: прошивка

    @Test
    void releaseFirmwareCreatesThreeTasksPerSkuForItsOwner() {
        String version = "7." + marker();
        FirmwareForm form = new FirmwareForm();
        form.setVersion(version);
        form.setSkus("WEP-30L, WOP-30L");
        form.setStatus("RELEASE");
        form.setReleaseDate(LocalDate.now().toString());
        var fw = firmware.create(form, new MockMultipartFile("file", "f.bin", "application/octet-stream",
                "x".getBytes(StandardCharsets.UTF_8)), product).firmware();

        List<Task> created = withTitle("прошивка " + version);
        assertEquals(6, created.size(), "по три задачи на каждый из двух SKU");
        for (String model : List.of("WEP-30L", "WOP-30L")) {
            List<TaskType> typesForSku = created.stream().filter(t -> t.getSku().getModel().equals(model))
                    .map(Task::getType).toList();
            assertTrue(typesForSku.containsAll(List.of(TaskType.CHECK_DATASHEET, TaskType.CHECK_BATTLECARD, TaskType.UPDATE_SITE)), model);
            assertEquals(3, typesForSku.size());
        }
        assertTrue(created.stream().allMatch(t -> t.getAssignee().getLogin().equals(t.getSku().getOwner().getLogin())), "основной владелец SKU");
        assertTrue(created.stream().allMatch(t -> t.getRelatedType() == RelatedType.FIRMWARE
                && t.getRelatedId().equals(fw.getId()) && t.getRelatedUrl().equals("/firmware/" + fw.getId())), "со ссылкой на прошивку");
        assertTrue(created.stream().allMatch(t -> t.getStatus() == TaskStatus.NEW && t.getDueDate() != null));
        assertTrue(created.stream().allMatch(t -> t.getCreatedBy().equals("SYSTEM")));
    }

    @Test
    void betaFirmwareCreatesNoTasksAndRepeatedEventDoesNotDuplicate() {
        String version = "6." + marker();
        FirmwareForm form = new FirmwareForm();
        form.setVersion(version);
        form.setSkus("WEP-30L");
        form.setStatus("BETA");
        form.setReleaseDate(LocalDate.now().toString());
        var fw = firmware.create(form, new MockMultipartFile("file", "b.bin", "application/octet-stream", "b".getBytes()), product).firmware();
        assertTrue(withTitle("прошивка " + version).isEmpty());

        firmware.changeStatus(fw.getId(), FirmwareStatus.RELEASE, product);
        assertEquals(3, withTitle("прошивка " + version).size());
        // то же событие еще раз (например, повторная публикация): дублей нет
        publisher.publishEvent(new FirmwarePublished(fw.getId(), version, List.of(sku("WEP-30L").getId()), "product"));
        assertEquals(3, withTitle("прошивка " + version).size());
    }

    // ----------------------------------------------------------- триггер: вопросы и правки, путь вопроса

    @Test
    void questionCreatesTaskForOwnerAndAnswerClosesIt() {
        String text = "Сколько портов у " + marker() + "?";
        var q = questions.ask(QuestionKind.QUESTION, TargetType.SKU, sku("WEP-550K").getId(), text, false, sales);
        Task t = all().stream().filter(x -> x.getRelatedType() == RelatedType.QUESTION && x.getRelatedId().equals(q.getId()))
                .findFirst().orElseThrow();
        assertEquals(TaskType.ANSWER_QUESTION, t.getType());
        assertEquals("kuznetsov", t.getAssignee().getLogin());
        assertEquals(TaskStatus.NEW, t.getStatus());
        assertEquals("/sku/WEP-550K", t.getRelatedUrl());

        questions.answer(q.getId(), "Один порт", true, product);
        Task after = tasks.detail(t.getId(), product);
        assertEquals(TaskStatus.DONE, after.getStatus(), "ответ закрывает задачу");
        assertNotNull(after.getClosedAt());
    }

    @Test
    void editProposalCreatesReviewTask() {
        var q = questions.ask(QuestionKind.EDIT_PROPOSAL, TargetType.SKU, sku("WEP-550K").getId(), "Правка " + marker(), false, sales);
        Task t = all().stream().filter(x -> x.getRelatedType() == RelatedType.QUESTION && x.getRelatedId().equals(q.getId()))
                .findFirst().orElseThrow();
        assertEquals(TaskType.REVIEW_EDIT, t.getType());
        questions.resolveEdit(q.getId(), false, "нет", product);
        assertEquals(TaskStatus.DONE, tasks.detail(t.getId(), product).getStatus());
    }

    @Test
    void questionTaskCannotBeClosedWithoutAnswer() {
        var q = questions.ask(QuestionKind.QUESTION, TargetType.SKU, sku("WEP-550K").getId(), "Без ответа " + marker(), false, sales);
        Task t = all().stream().filter(x -> x.getRelatedType() == RelatedType.QUESTION && x.getRelatedId().equals(q.getId())).findFirst().orElseThrow();
        var e = assertThrows(TaskRuleException.class, () -> tasks.move(t.getId(), TaskStatus.DONE, product));
        assertEquals("tasks.rule.answerFirst", e.getMessageKey());
        // в работу и на проверку можно, вопрос при этом переходит в работу
        tasks.move(t.getId(), TaskStatus.IN_PROGRESS, product);
        assertEquals(ru.eltex.producthub.qa.domain.QuestionStatus.IN_PROGRESS, questions.get(q.getId()).getStatus());
        tasks.move(t.getId(), TaskStatus.REVIEW, product);

        var edit = questions.ask(QuestionKind.EDIT_PROPOSAL, TargetType.SKU, sku("WEP-550K").getId(), "Без решения " + marker(), false, sales);
        Task te = all().stream().filter(x -> x.getRelatedType() == RelatedType.QUESTION && x.getRelatedId().equals(edit.getId())).findFirst().orElseThrow();
        assertEquals("tasks.rule.resolveFirst", assertThrows(TaskRuleException.class,
                () -> tasks.move(te.getId(), TaskStatus.DONE, product)).getMessageKey());
    }

    @Test
    void questionAboutArtifactGoesToArtifactOwner() {
        Artifact a = artifactService.create(new ArtifactDraft("Материал " + marker(), null,
                types.byName("Даташит").orElseThrow(), Language.NONE, ArtifactStatus.ACTUAL, kuznetsovUser(), null, null,
                null, List.of(sku("WEP-550K")), List.of(), null), List.of(file("a.pdf", pdf())), null, product);
        var q = questions.ask(QuestionKind.QUESTION, TargetType.ARTIFACT, a.getId(), "Актуален ли " + marker() + "?", false, sales);
        Task t = all().stream().filter(x -> x.getRelatedType() == RelatedType.QUESTION && x.getRelatedId().equals(q.getId())).findFirst().orElseThrow();
        assertEquals("kuznetsov", t.getAssignee().getLogin());
        assertEquals("/artifacts/" + a.getId(), t.getRelatedUrl());
    }

    private ru.eltex.producthub.security.User kuznetsovUser() {
        return sku("WEP-550K").getOwner();
    }

    // ----------------------------------------------------------- ежедневные проверки

    @Test
    void staleArtifactGetsTaskOnceAndKeepsItsStatus() {
        Artifact a = artifactService.create(new ArtifactDraft("Старый " + marker(), null, types.byName("Даташит").orElseThrow(),
                Language.NONE, ArtifactStatus.ACTUAL, kuznetsovUser(), null, null, null, List.of(), List.of(), null),
                List.of(file("old.pdf", pdf())), null, product);
        Artifact stored = artifactRepository.findById(a.getId()).orElseThrow();
        stored.markReviewed(LocalDateTime.now().minusDays(400));
        artifactRepository.save(stored);

        stalenessJob.run();
        List<Task> found = withTitle(a.getTitle());
        assertEquals(1, found.size());
        assertEquals(TaskType.REVIEW_STALE_ARTIFACT, found.get(0).getType());
        assertEquals("kuznetsov", found.get(0).getAssignee().getLogin());
        assertEquals(ArtifactStatus.ACTUAL, artifactService.detail(a.getId(), product).artifact().getStatus(), "статус не меняется");

        stalenessJob.run();
        assertEquals(1, withTitle(a.getTitle()).size(), "повторная проверка дубль не создает");
    }

    @Test
    void freshArtifactGetsNoStaleTask() {
        Artifact a = artifactService.create(new ArtifactDraft("Свежий " + marker(), null, types.byName("Даташит").orElseThrow(),
                Language.NONE, ArtifactStatus.ACTUAL, kuznetsovUser(), null, null, null, List.of(), List.of(), null),
                List.of(file("new.pdf", pdf())), null, product);
        stalenessJob.run();
        assertTrue(withTitle(a.getTitle()).isEmpty());
    }

    @Test
    void skuWithPastReviewDateGetsReviewTask() {
        Sku s = newSku("REV");
        SkuForm form = skuService.formFor(s.getId(), product);
        form.setNextReviewDate(LocalDate.now().minusDays(1).toString());
        skuService.update(s.getId(), SkuBlock.LIFECYCLE, form, product);

        reviewJob.run();
        List<Task> found = withTitle("ревью SKU: " + s.getModel());
        assertEquals(1, found.size());
        assertEquals(TaskType.REVIEW_SKU, found.get(0).getType());
        reviewJob.run();
        assertEquals(1, withTitle("ревью SKU: " + s.getModel()).size());
    }

    private Sku newSku(String prefix) {
        SkuForm f = new SkuForm();
        f.setModel("TASK-" + prefix + "-" + marker());
        f.setLineId(sku("WEP-550K").getLine().getId());
        f.setCategory("Точка доступа внутренняя");
        f.setOwnerId(sku("WEP-550K").getOwner().getId());
        f.setLifecycleStatus("PRODUCTION");
        return skuService.create(f, product);
    }

    // ----------------------------------------------------------- EOL_ANNOUNCED

    @Test
    void eolAnnouncedCreatesSiteTaskAndMigrationGuideTask() {
        Sku s = newSku("EOL");
        assertTrue(withTitle(s.getModel()).isEmpty());
        SkuForm form = skuService.formFor(s.getId(), product);
        form.setLifecycleStatus("EOL_ANNOUNCED");
        form.setEolAnnounceDate("1Q27");
        skuService.update(s.getId(), SkuBlock.LIFECYCLE, form, product);

        List<Task> found = withTitle(s.getModel());
        assertEquals(2, found.size(), found.stream().map(Task::getTitle).toList().toString());
        assertTrue(found.stream().anyMatch(t -> t.getType() == TaskType.UPDATE_SITE));
        assertTrue(found.stream().anyMatch(t -> t.getType() == TaskType.MANUAL && t.getTitle().contains("migration guide")));

        // повторный перевод в тот же статус: дублей нет
        form.setLifecycleStatus("EOL");
        skuService.update(s.getId(), SkuBlock.LIFECYCLE, form, product);
        form.setLifecycleStatus("EOL_ANNOUNCED");
        skuService.update(s.getId(), SkuBlock.LIFECYCLE, form, product);
        assertEquals(2, withTitle(s.getModel()).size());
    }

    @Test
    void otherLifecycleChangesCreateNoTasks() {
        Sku s = newSku("NEW");
        SkuForm form = skuService.formFor(s.getId(), product);
        form.setLifecycleStatus("EOL");
        skuService.update(s.getId(), SkuBlock.LIFECYCLE, form, product);
        assertTrue(withTitle(s.getModel()).isEmpty());
    }

    // ----------------------------------------------------------- TRANSLATE

    private Artifact datasheet(String title, Language language, Sku... skuList) {
        return artifactService.create(new ArtifactDraft(title, null, types.byName("Даташит").orElseThrow(), language,
                ArtifactStatus.ACTUAL, kuznetsovUser(), null, null, null, List.of(skuList), List.of(), null),
                List.of(file(title + ".pdf", pdf())), null, product);
    }

    @Test
    void ruArtifactWithoutEnPairCreatesTranslateTaskAndPairClosesIt() {
        Sku s = newSku("TR");
        String title = "Даташит " + marker();
        Artifact ru = datasheet(title + " RU", Language.RU, s);
        List<Task> open = withTitle(title + " RU");
        assertEquals(1, open.size());
        assertEquals(TaskType.TRANSLATE, open.get(0).getType());
        assertEquals("kuznetsov", open.get(0).getAssignee().getLogin(), "владелец артефакта");

        datasheet(title + " EN", Language.EN, s);
        assertEquals(TaskStatus.DONE, tasks.detail(open.get(0).getId(), product).getStatus(), "пара появилась - задача закрыта");
        assertTrue(withTitle(title + " EN").isEmpty(), "у EN теперь есть пара, задачи нет");
        assertNotNull(ru.getId());
    }

    @Test
    void enArtifactWithoutRuPairCreatesTranslateTask() {
        Sku s = newSku("TR2");
        String title = "Datasheet " + marker();
        datasheet(title, Language.EN, s);
        assertEquals(1, withTitle(title).size());
    }

    @Test
    void pairOfDifferentTypeDoesNotCount() {
        Sku s = newSku("TR3");
        String title = "Разные типы " + marker();
        datasheet(title + " RU", Language.RU, s);
        artifactService.create(new ArtifactDraft(title + " EN", null, types.byName("Battle card").orElseThrow(), Language.EN,
                ArtifactStatus.ACTUAL, kuznetsovUser(), null, null, null, List.of(s), List.of(), null),
                List.of(file("bc.pdf", pdf())), null, product);
        assertEquals(TaskStatus.NEW, withTitle(title + " RU").get(0).getStatus());
    }

    @Test
    void bilingualOrNoLanguageOrNoSkuArtifactsNeedNoTranslation() {
        Sku s = newSku("TR4");
        String title = "Без перевода " + marker();
        datasheet(title + " A", Language.RU_EN, s);
        datasheet(title + " B", Language.NONE, s);
        datasheet(title + " C", Language.RU);
        assertTrue(withTitle(title).isEmpty());
    }

    // ----------------------------------------------------------- права и доска

    @Test
    void boardIsForProductAdminHeadAndServiceCenterOnly() {
        assertNotNull(tasks.board(TaskFilter.defaults(), product));
        assertNotNull(tasks.board(TaskFilter.defaults(), admin));
        assertNotNull(tasks.board(TaskFilter.defaults(), head));
        assertNotNull(tasks.board(TaskFilter.defaults(), serviceCenter));
        assertThrows(AccessDeniedException.class, () -> tasks.board(TaskFilter.defaults(), sales));
        assertThrows(AccessDeniedException.class, () -> tasks.board(TaskFilter.defaults(), users.loadUserByUsername("rnd")));
    }

    @Test
    void serviceCenterSeesOnlyItsOwnTasksEvenWithAllFilter() {
        TaskForm own = manual("Своя " + marker());
        Task mine = tasks.createManual(own, serviceCenter);
        Task foreign = tasks.createManual(manual("Чужая " + marker()), product);
        var visible = tasks.board(new TaskFilter("all", null, null, null), serviceCenter).columns().values().stream()
                .flatMap(List::stream).toList();
        assertTrue(visible.stream().anyMatch(t -> t.getId().equals(mine.getId())));
        assertTrue(visible.stream().noneMatch(t -> t.getId().equals(foreign.getId())));
        assertTrue(visible.stream().allMatch(t -> t.getAssignee().getLogin().equals("service")));
        assertThrows(AccessDeniedException.class, () -> tasks.detail(foreign.getId(), serviceCenter));
    }

    @Test
    void assigneeFilterMeAllAndSpecific() {
        TaskForm f = manual("Для Смита " + marker());
        f.setAssigneeId(users.loadUserByUsername("smith").getId());
        Task t = tasks.createManual(f, product);
        assertTrue(tasks.board(new TaskFilter("me", null, null, null), product).columns().values().stream()
                .flatMap(List::stream).noneMatch(x -> x.getId().equals(t.getId())));
        assertTrue(tasks.board(new TaskFilter("all", null, null, null), product).columns().values().stream()
                .flatMap(List::stream).anyMatch(x -> x.getId().equals(t.getId())));
        assertTrue(tasks.board(new TaskFilter(String.valueOf(t.getAssignee().getId()), null, null, null), head).columns().values()
                .stream().flatMap(List::stream).anyMatch(x -> x.getId().equals(t.getId())));
    }

    @Test
    void filtersByTypeSkuAndLine() {
        Sku s = newSku("FLT");
        TaskForm f = manual("Фильтр " + marker());
        f.setSku(s.getModel());
        f.setType("REVIEW_SKU");
        Task t = tasks.createManual(f, product);
        assertTrue(tasks.board(new TaskFilter("all", "REVIEW_SKU", s.getModel(), null), product).columns().get(TaskStatus.NEW).stream()
                .anyMatch(x -> x.getId().equals(t.getId())));
        assertTrue(tasks.board(new TaskFilter("all", "TRANSLATE", s.getModel(), null), product).columns().get(TaskStatus.NEW).isEmpty());
        assertTrue(tasks.board(new TaskFilter("all", null, null, s.getLine().getId()), product).columns().get(TaskStatus.NEW).stream()
                .anyMatch(x -> x.getId().equals(t.getId())));
        assertTrue(tasks.board(new TaskFilter("all", null, "НЕТ-ТАКОГО-SKU", null), product).columns().get(TaskStatus.NEW).isEmpty());
    }

    @Test
    void movePermissions() {
        Task t = tasks.createManual(manual("Двигать " + marker()), product);
        assertThrows(AccessDeniedException.class, () -> tasks.move(t.getId(), TaskStatus.IN_PROGRESS, head), "HEAD только смотрит");
        assertThrows(AccessDeniedException.class, () -> tasks.move(t.getId(), TaskStatus.IN_PROGRESS, serviceCenter), "чужая задача");
        assertEquals(TaskStatus.IN_PROGRESS, tasks.move(t.getId(), TaskStatus.IN_PROGRESS, admin).getStatus());
        assertEquals(TaskStatus.DONE, tasks.move(t.getId(), TaskStatus.DONE, product).getStatus());
        assertNotNull(tasks.detail(t.getId(), product).getClosedAt());
        assertEquals(TaskStatus.NEW, tasks.move(t.getId(), TaskStatus.NEW, product).getStatus());
        assertEquals(null, tasks.detail(t.getId(), product).getClosedAt(), "при возврате закрытие сбрасывается");

        Task own = tasks.createManual(manual("Своя двигать " + marker()), serviceCenter);
        assertEquals(TaskStatus.REVIEW, tasks.move(own.getId(), TaskStatus.REVIEW, serviceCenter).getStatus());
    }

    @Test
    void manualTaskRules() {
        assertThrows(AccessDeniedException.class, () -> tasks.createManual(manual("x"), sales));
        assertThrows(AccessDeniedException.class, () -> tasks.createManual(manual("x"), head));
        // сервисный центр может создать задачу только себе
        TaskForm f = manual("СЦ себе " + marker());
        f.setAssigneeId(kuznetsov.getId());
        assertEquals("service", tasks.createManual(f, serviceCenter).getAssignee().getLogin());
        var errors = assertThrows(FormValidationException.class, () -> tasks.createManual(new TaskForm(), product)).getErrors();
        assertTrue(errors.containsKey("title"));
        TaskForm bad = manual("Плохой " + marker());
        bad.setSku("НЕТ-ТАКОГО");
        assertTrue(assertThrows(FormValidationException.class, () -> tasks.createManual(bad, product)).getErrors().containsKey("sku"));
        bad.setSku(null);
        bad.setDueDate("завтра");
        assertTrue(assertThrows(FormValidationException.class, () -> tasks.createManual(bad, product)).getErrors().containsKey("dueDate"));
    }

    @Test
    void reassignOnlyByProductOrAdmin() {
        Task t = tasks.createManual(manual("Переназначить " + marker()), product);
        Long smith = users.loadUserByUsername("smith").getId();
        assertThrows(AccessDeniedException.class, () -> tasks.reassign(t.getId(), smith, serviceCenter));
        tasks.reassign(t.getId(), smith, admin);
        assertEquals("smith", tasks.detail(t.getId(), product).getAssignee().getLogin());
    }

    @Test
    void newTasksCounterFollowsStatus() {
        long before = tasks.newCount(product.getId());
        Task t = tasks.createManual(manual("Счетчик " + marker()), product);
        assertEquals(before + 1, tasks.newCount(product.getId()));
        tasks.move(t.getId(), TaskStatus.IN_PROGRESS, product);
        assertEquals(before, tasks.newCount(product.getId()));
    }

    private TaskForm manual(String title) {
        TaskForm f = new TaskForm();
        f.setTitle(title);
        f.setAssigneeId(product.getId());
        return f;
    }

    @Test
    void anyOpenTaskOfSameTypeAndObjectBlocksDuplicate() {
        Sku s = newSku("DUP");
        var spec = new ru.eltex.producthub.task.service.TaskSpec(TaskType.REVIEW_SKU, "Ревью " + s.getModel(), null,
                s.getOwner(), RelatedType.SKU, s.getId(), s.getModel(), "/sku/" + s.getModel(), s.getId(), "test");
        var first = tasks.createIfAbsent(spec);
        assertTrue(first.isPresent());
        assertFalse(tasks.createIfAbsent(spec).isPresent(), "пока первая открыта, вторая не создается");
        tasks.move(first.get().getId(), TaskStatus.DONE, product);
        assertTrue(tasks.createIfAbsent(spec).isPresent(), "после закрытия можно создать снова");
    }

    @Test
    void taskWithoutOwnerIsNotCreated() {
        var spec = new ru.eltex.producthub.task.service.TaskSpec(TaskType.REVIEW_SKU, "Без владельца", null, null,
                RelatedType.SKU, 1L, "x", "/x", null, "test");
        assertFalse(tasks.createIfAbsent(spec).isPresent());
    }
}
