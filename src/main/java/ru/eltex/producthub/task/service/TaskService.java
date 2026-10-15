package ru.eltex.producthub.task.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.qa.domain.Question;
import ru.eltex.producthub.qa.service.QuestionService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.Role;
import ru.eltex.producthub.security.User;
import ru.eltex.producthub.security.UserRepository;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.repo.SkuRepository;
import ru.eltex.producthub.sku.service.ModelNormalizer;
import ru.eltex.producthub.task.domain.RelatedType;
import ru.eltex.producthub.task.domain.Task;
import ru.eltex.producthub.task.domain.TaskPriority;
import ru.eltex.producthub.task.domain.TaskStatus;
import ru.eltex.producthub.task.domain.TaskType;
import ru.eltex.producthub.task.repo.TaskQueryRepository;
import ru.eltex.producthub.task.repo.TaskRepository;

/**
 * Задачи и канбан (SPEC.md 5.9). Права: PRODUCT и ADMIN видят и двигают все задачи, HEAD только смотрит все,
 * SERVICE_CENTER работает со своими, SALES и RND доски не видят (у них "Мои вопросы").
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository repository;
    private final TaskQueryRepository queries;
    private final UserRepository users;
    private final SkuRepository skus;
    private final QuestionService questions;
    private final TaskProperties props;

    public TaskService(TaskRepository repository, TaskQueryRepository queries, UserRepository users,
                       SkuRepository skus, QuestionService questions, TaskProperties props) {
        this.repository = repository;
        this.queries = queries;
        this.users = users;
        this.skus = skus;
        this.questions = questions;
        this.props = props;
    }

    // ------------------------------------------------------------------ права

    private static boolean has(HubUserDetails user, Role... roles) {
        for (Role r : roles) {
            if (user.getRoles().contains(r)) {
                return true;
            }
        }
        return false;
    }

    public boolean canManageAll(HubUserDetails user) {
        return has(user, Role.PRODUCT, Role.ADMIN);
    }

    public boolean canSeeAll(HubUserDetails user) {
        return has(user, Role.PRODUCT, Role.ADMIN, Role.HEAD);
    }

    public boolean canUseBoard(HubUserDetails user) {
        return canSeeAll(user) || has(user, Role.SERVICE_CENTER);
    }

    public boolean canMove(HubUserDetails user, Task task) {
        return canManageAll(user)
                || (has(user, Role.SERVICE_CENTER) && task.getAssignee().getId().equals(user.getId()));
    }

    public boolean canCreate(HubUserDetails user) {
        return has(user, Role.PRODUCT, Role.ADMIN, Role.SERVICE_CENTER);
    }

    // ------------------------------------------------------------------ создание (триггеры)

    /** Создает задачу, если такой открытой еще нет. Возвращает пусто, если это дубль или нет исполнителя. */
    @Transactional
    public Optional<Task> createIfAbsent(TaskSpec spec) {
        if (spec.assignee() == null) {
            log.warn("Задача \"{}\" не создана: у объекта нет владельца", spec.title());
            return Optional.empty();
        }
        if (isDuplicate(spec)) {
            return Optional.empty();
        }
        Task t = new Task(spec.title(), spec.type(), spec.assignee(), "SYSTEM");
        t.setDescription(spec.description());
        t.setRelated(spec.relatedType(), spec.relatedId(), spec.relatedLabel(), spec.relatedUrl());
        t.setSourceEvent(spec.sourceEvent());
        if (spec.skuId() != null) {
            t.setSku(skus.getReferenceById(spec.skuId()));
        }
        int days = spec.type() == TaskType.ANSWER_QUESTION || spec.type() == TaskType.REVIEW_EDIT
                ? props.questionDueDays() : props.otherDueDays();
        t.setDueDate(LocalDate.now().plusDays(days));
        return Optional.of(repository.save(t));
    }

    private boolean isDuplicate(TaskSpec spec) {
        if (spec.relatedType() == null || spec.relatedId() == null) {
            return false;
        }
        if (spec.type() == TaskType.MANUAL) {
            return repository.countOpenByTitle(spec.type(), spec.relatedType(), spec.relatedId(), spec.title()) > 0;
        }
        if (spec.skuId() != null) {
            return repository.countOpenForSku(spec.type(), spec.relatedType(), spec.relatedId(), spec.skuId()) > 0;
        }
        return repository.countOpenWithoutSku(spec.type(), spec.relatedType(), spec.relatedId()) > 0;
    }

    /** Ручная задача: PRODUCT и ADMIN любому исполнителю, сервисный центр только себе. */
    @Transactional
    public Task createManual(TaskForm form, HubUserDetails user) {
        if (!canCreate(user)) {
            throw new AccessDeniedException("Нет прав на создание задач");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        String title = form.getTitle() == null ? "" : form.getTitle().trim();
        if (title.isEmpty()) {
            errors.put("title", "validation.required");
        }
        TaskType type = TaskType.MANUAL;
        try {
            type = TaskType.valueOf(form.getType());
        } catch (IllegalArgumentException | NullPointerException e) {
            errors.put("type", "validation.required");
        }
        TaskPriority priority = TaskPriority.NORMAL;
        try {
            priority = TaskPriority.valueOf(form.getPriority());
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // по умолчанию обычный
        }
        Long assigneeId = canManageAll(user) ? form.getAssigneeId() : user.getId();
        User assignee = assigneeId == null ? null : users.findById(assigneeId).filter(User::isActive).orElse(null);
        if (assignee == null) {
            errors.put("assigneeId", "validation.required");
        }
        LocalDate due = null;
        if (form.getDueDate() != null && !form.getDueDate().isBlank()) {
            try {
                due = LocalDate.parse(form.getDueDate().trim());
            } catch (java.time.format.DateTimeParseException e) {
                errors.put("dueDate", "validation.date.invalid");
            }
        }
        Sku sku = null;
        if (form.getSku() != null && !form.getSku().isBlank()) {
            sku = skus.findByModelNormalized(ModelNormalizer.normalize(form.getSku())).filter(s -> !s.isDeleted()).orElse(null);
            if (sku == null) {
                errors.put("sku", "validation.sku.notFound");
            }
        }
        if (!errors.isEmpty()) {
            throw new FormValidationException(errors);
        }
        Task t = new Task(title, type, assignee, user.getUsername());
        t.setDescription(form.getDescription() == null || form.getDescription().isBlank() ? null : form.getDescription().trim());
        t.setPriority(priority);
        t.setDueDate(due);
        t.setSourceEvent("MANUAL");
        if (sku != null) {
            t.setSku(sku);
            t.setRelated(RelatedType.SKU, sku.getId(), sku.getModel(), "/sku/" + sku.getModel());
        }
        return repository.save(t);
    }

    // ------------------------------------------------------------------ доска

    @Transactional(readOnly = true)
    public Board board(TaskFilter filter, HubUserDetails user) {
        if (!canUseBoard(user)) {
            throw new AccessDeniedException("Доска задач недоступна");
        }
        Long assigneeId;
        if (!canSeeAll(user)) {
            assigneeId = user.getId();
        } else if (filter.assignee() == null || filter.assignee().equals("me")) {
            assigneeId = user.getId();
        } else if (filter.assignee().equals("all")) {
            assigneeId = null;
        } else {
            try {
                assigneeId = Long.valueOf(filter.assignee());
            } catch (NumberFormatException e) {
                assigneeId = user.getId();
            }
        }
        TaskType type = null;
        try {
            type = filter.type() == null || filter.type().isBlank() ? null : TaskType.valueOf(filter.type());
        } catch (IllegalArgumentException ignored) {
            // неизвестный тип игнорируем
        }
        List<Task> tasks = queries.board(assigneeId, type, filter.sku(), filter.line(),
                LocalDateTime.now().minusDays(props.doneVisibleDays()));
        Map<TaskStatus, List<Task>> columns = new EnumMap<>(TaskStatus.class);
        for (TaskStatus s : TaskStatus.values()) {
            columns.put(s, new ArrayList<>());
        }
        for (Task t : tasks) {
            columns.get(t.getStatus()).add(t);
        }
        return new Board(columns, canManageAll(user), canSeeAll(user));
    }

    @Transactional(readOnly = true)
    public Task detail(Long id, HubUserDetails user) {
        Task t = repository.findDetailed(id).orElseThrow(() -> new TaskNotFoundException(id));
        if (!canSeeAll(user) && !t.getAssignee().getId().equals(user.getId())) {
            throw new AccessDeniedException("Чужая задача");
        }
        return t;
    }

    /** Сменить колонку (перетаскивание). Задачу на вопрос или правку нельзя закрыть без ответа. */
    @Transactional
    public Task move(Long id, TaskStatus status, HubUserDetails user) {
        Task t = repository.findDetailed(id).orElseThrow(() -> new TaskNotFoundException(id));
        if (!canMove(user, t)) {
            throw new AccessDeniedException("Нет прав менять эту задачу");
        }
        if (status == TaskStatus.DONE && t.getStatus() != TaskStatus.DONE
                && (t.getType() == TaskType.ANSWER_QUESTION || t.getType() == TaskType.REVIEW_EDIT)
                && t.getRelatedType() == RelatedType.QUESTION) {
            Question q = questions.get(t.getRelatedId());
            if (!questions.isResolved(q)) {
                throw new TaskRuleException(t.getType() == TaskType.ANSWER_QUESTION
                        ? "tasks.rule.answerFirst" : "tasks.rule.resolveFirst");
            }
        }
        t.moveTo(status);
        if (status == TaskStatus.IN_PROGRESS && t.getRelatedType() == RelatedType.QUESTION) {
            questions.markInProgress(t.getRelatedId());
        }
        return t;
    }

    @Transactional
    public void reassign(Long id, Long assigneeId, HubUserDetails user) {
        if (!canManageAll(user)) {
            throw new AccessDeniedException("Переназначать задачи могут PRODUCT и ADMIN");
        }
        Task t = repository.findDetailed(id).orElseThrow(() -> new TaskNotFoundException(id));
        User assignee = users.findById(assigneeId).filter(User::isActive).orElseThrow();
        t.setAssignee(assignee);
    }

    /** Закрывает открытые задачи по вопросу (вызывается, когда вопрос решен). */
    @Transactional
    public int closeFor(RelatedType type, Long relatedId) {
        List<Task> open = repository.findOpenByRelated(type, relatedId);
        open.forEach(t -> t.moveTo(TaskStatus.DONE));
        return open.size();
    }

    @Transactional
    public void closeSystem(Task task) {
        task.moveTo(TaskStatus.DONE);
        repository.save(task);
    }

    @Transactional(readOnly = true)
    public List<Task> openOfType(TaskType type) {
        return repository.findOpenOfType(type);
    }

    @Transactional(readOnly = true)
    public long newCount(Long userId) {
        return repository.countByAssigneeIdAndStatus(userId, TaskStatus.NEW);
    }

    /** Пользователи, которых можно назначить исполнителем. */
    @Transactional(readOnly = true)
    public List<User> assignableUsers() {
        return Stream.concat(users.findActiveByRole(Role.PRODUCT).stream(), users.findActiveByRole(Role.SERVICE_CENTER).stream())
                .distinct().toList();
    }
}
