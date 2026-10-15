package ru.eltex.producthub.task.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.artifact.domain.Language;
import ru.eltex.producthub.artifact.event.ArtifactStale;
import ru.eltex.producthub.artifact.event.ArtifactUpdated;
import ru.eltex.producthub.artifact.repo.ArtifactRepository;
import ru.eltex.producthub.firmware.event.FirmwarePublished;
import ru.eltex.producthub.qa.domain.Question;
import ru.eltex.producthub.qa.domain.QuestionKind;
import ru.eltex.producthub.qa.event.EditProposed;
import ru.eltex.producthub.qa.event.QuestionCreated;
import ru.eltex.producthub.qa.event.QuestionResolved;
import ru.eltex.producthub.qa.service.QuestionService;
import ru.eltex.producthub.qa.service.QuestionTargets;
import ru.eltex.producthub.sku.domain.LifecycleStatus;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.event.SkuLifecycleChanged;
import ru.eltex.producthub.sku.event.SkuReviewDue;
import ru.eltex.producthub.sku.repo.SkuRepository;
import ru.eltex.producthub.task.domain.RelatedType;
import ru.eltex.producthub.task.domain.Task;
import ru.eltex.producthub.task.domain.TaskType;

/**
 * Триггеры задач (SPEC.md 5.9). Модуль слушает события других модулей, а те о задачах ничего не знают.
 * Дубли не создаются: открытая задача того же типа на тот же объект блокирует новую.
 */
@Component
@Transactional
public class TaskTriggers {

    private static final Logger log = LoggerFactory.getLogger(TaskTriggers.class);

    private final TaskService tasks;
    private final SkuRepository skus;
    private final ArtifactRepository artifacts;
    private final QuestionService questions;
    private final QuestionTargets targets;

    public TaskTriggers(TaskService tasks, SkuRepository skus, ArtifactRepository artifacts, QuestionService questions,
                        QuestionTargets targets) {
        this.tasks = tasks;
        this.skus = skus;
        this.artifacts = artifacts;
        this.questions = questions;
        this.targets = targets;
    }

    /** Прошивка RELEASE: три задачи владельцу каждого затронутого SKU, по одной каждого типа. */
    @EventListener
    public void onFirmwarePublished(FirmwarePublished e) {
        for (Long skuId : e.skuIds()) {
            skus.findCardById(skuId).ifPresent(sku -> {
                for (TaskType type : List.of(TaskType.CHECK_DATASHEET, TaskType.CHECK_BATTLECARD, TaskType.UPDATE_SITE)) {
                    String action = switch (type) {
                        case CHECK_DATASHEET -> "Проверить даташит";
                        case CHECK_BATTLECARD -> "Проверить battle cards";
                        default -> "Обновить сайт";
                    };
                    tasks.createIfAbsent(new TaskSpec(type, action + ": " + sku.getModel() + " (прошивка " + e.version() + ")",
                            "Опубликована новая прошивка " + e.version() + ". Проверьте, что материалы и сайт актуальны.",
                            sku.getOwner(), RelatedType.FIRMWARE, e.firmwareId(), "Прошивка " + e.version(),
                            "/firmware/" + e.firmwareId(), sku.getId(), "FirmwarePublished"));
                }
            });
        }
    }

    @EventListener
    public void onQuestionCreated(QuestionCreated e) {
        createQuestionTask(e.questionId(), TaskType.ANSWER_QUESTION, "Ответить на вопрос", "QuestionCreated");
    }

    @EventListener
    public void onEditProposed(EditProposed e) {
        createQuestionTask(e.questionId(), TaskType.REVIEW_EDIT, "Проверить предложенную правку", "EditProposed");
    }

    private void createQuestionTask(Long questionId, TaskType type, String prefix, String source) {
        Question q = questions.get(questionId);
        targets.resolve(q.getTargetType(), q.getTargetId()).ifPresent(t -> tasks.createIfAbsent(new TaskSpec(type,
                prefix + ": " + t.label(), q.getText(), t.owner(), RelatedType.QUESTION, questionId, t.label(),
                t.url(), t.skuId(), source)));
    }

    /** Вопрос решен (ответ, правка принята или отклонена, закрыт): задача закрывается сама. */
    @EventListener
    public void onQuestionResolved(QuestionResolved e) {
        tasks.closeFor(RelatedType.QUESTION, e.questionId());
    }

    /** Артефакт давно не ревьюили. Статус артефакта не меняется. */
    @EventListener
    public void onArtifactStale(ArtifactStale e) {
        artifacts.findCardById(e.artifactId()).ifPresent(a -> tasks.createIfAbsent(new TaskSpec(
                TaskType.REVIEW_STALE_ARTIFACT, "Проверить актуальность материала: " + a.getTitle(),
                "Материал не ревьюили дольше " + a.getReviewPeriodDays() + " дн.", a.getOwner(), RelatedType.ARTIFACT,
                a.getId(), a.getTitle(), "/artifacts/" + a.getId(), null, "ArtifactStale")));
    }

    @EventListener
    public void onSkuReviewDue(SkuReviewDue e) {
        skus.findCardById(e.skuId()).ifPresent(s -> tasks.createIfAbsent(new TaskSpec(TaskType.REVIEW_SKU,
                "Провести ревью SKU: " + s.getModel(), "Дата следующего ревью прошла.", s.getOwner(), RelatedType.SKU,
                s.getId(), s.getModel(), "/sku/" + s.getModel(), s.getId(), "SkuReviewDue")));
    }

    /** SKU переведен в EOL_ANNOUNCED: обновить сайт и проверить migration guide. */
    @EventListener
    public void onLifecycleChanged(SkuLifecycleChanged e) {
        if (e.newStatus() != LifecycleStatus.EOL_ANNOUNCED) {
            return;
        }
        skus.findCardById(e.skuId()).ifPresent(s -> {
            String url = "/sku/" + s.getModel();
            tasks.createIfAbsent(new TaskSpec(TaskType.UPDATE_SITE, "Обновить сайт: объявлен EOL для " + s.getModel(),
                    "Статус изменен на \"Анонс EOL\".", s.getOwner(), RelatedType.SKU, s.getId(), s.getModel(), url,
                    s.getId(), "SkuLifecycleChanged"));
            tasks.createIfAbsent(new TaskSpec(TaskType.MANUAL, "Проверить наличие migration guide: " + s.getModel(),
                    "Для SKU объявлен EOL, нужен migration guide на замену.", s.getOwner(), RelatedType.SKU, s.getId(),
                    s.getModel(), url, s.getId(), "SkuLifecycleChanged"));
        });
    }

    /**
     * Загружен артефакт RU без пары EN того же типа для того же SKU (или наоборот): задача на перевод владельцу
     * артефакта. Когда пара появляется, задача на перевод закрывается сама.
     */
    @EventListener
    public void onArtifactUpdated(ArtifactUpdated e) {
        if (e.created()) {
            artifacts.findCardById(e.artifactId()).ifPresent(this::createTranslateTask);
        }
        recheckTranslateTasks();
    }

    private void createTranslateTask(Artifact a) {
        List<Language> counterpart = counterpart(a.getLanguage());
        if (counterpart.isEmpty() || missingPairs(a, counterpart).isEmpty()) {
            return;
        }
        String target = a.getLanguage() == Language.RU ? "EN" : "RU";
        tasks.createIfAbsent(new TaskSpec(TaskType.TRANSLATE, "Подготовить версию " + target + ": " + a.getTitle(),
                "Для материала нет пары на другом языке у SKU: " + String.join(", ", missingPairs(a, counterpart)),
                a.getOwner(), RelatedType.ARTIFACT, a.getId(), a.getTitle(), "/artifacts/" + a.getId(), null,
                "ArtifactUpdated"));
    }

    private void recheckTranslateTasks() {
        for (Task t : tasks.openOfType(TaskType.TRANSLATE)) {
            Artifact a = artifacts.findCardById(t.getRelatedId()).orElse(null);
            if (a == null || counterpart(a.getLanguage()).isEmpty() || missingPairs(a, counterpart(a.getLanguage())).isEmpty()) {
                tasks.closeSystem(t);
                log.info("Задача на перевод №{} закрыта: пара найдена", t.getId());
            }
        }
    }

    private static List<Language> counterpart(Language language) {
        return switch (language) {
            case RU -> List.of(Language.EN, Language.RU_EN);
            case EN -> List.of(Language.RU, Language.RU_EN);
            default -> List.of();
        };
    }

    /** Артикулы, у которых нет пары. */
    private List<String> missingPairs(Artifact a, List<Language> counterpart) {
        List<String> missing = new java.util.ArrayList<>();
        for (Long skuId : artifacts.findSkuIds(a.getId())) {
            if (artifacts.countCounterparts(a.getType().getId(), counterpart, skuId, a.getId()) == 0) {
                missing.add(skus.findById(skuId).map(Sku::getModel).orElse(String.valueOf(skuId)));
            }
        }
        return missing;
    }

    @SuppressWarnings("unused")
    private static boolean isQuestion(Question q) {
        return q.getKind() == QuestionKind.QUESTION;
    }
}
