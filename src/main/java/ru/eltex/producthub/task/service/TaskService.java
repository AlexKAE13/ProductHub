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
import ru.eltex.producthub.task.domain.TaskBoard;
import ru.eltex.producthub.task.domain.TaskPriority;
import ru.eltex.producthub.task.domain.TaskStatus;
import ru.eltex.producthub.task.domain.TaskType;
import ru.eltex.producthub.task.repo.TaskBoardRepository;
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
    private final TaskBoardRepository boards;
    private final BoardActivity activity;

    public TaskService(TaskRepository repository, TaskQueryRepository queries, UserRepository users,
                       SkuRepository skus, QuestionService questions, TaskProperties props,
                       TaskBoardRepository boards, BoardActivity activity) {
        this.boards = boards;
        this.activity = activity;
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

    /** Администратор двигает и переназначает любые входящие задачи. */
    public boolean canManageAll(HubUserDetails user) {
        return has(user, Role.ADMIN);
    }

    /** Входящие запросы видны целиком только руководителю и администратору, остальные видят свои. */
    public boolean canSeeAll(HubUserDetails user) {
        return has(user, Role.HEAD, Role.ADMIN);
    }

    /** Системная доска "Входящие запросы" (SALES и RND ее не видят: у них "Мои вопросы"). */
    public boolean canUseBoard(HubUserDetails user) {
        return has(user, Role.PRODUCT, Role.ADMIN, Role.HEAD, Role.SERVICE_CENTER);
    }

    /** Свои доски доступны всем внутренним ролям. */
    public boolean canUseOwnBoards(HubUserDetails user) {
        return has(user, Role.PRODUCT, Role.ADMIN, Role.HEAD, Role.SERVICE_CENTER, Role.SALES, Role.RND);
    }

    public boolean isBoardMember(Long boardId, Long userId) {
        return boards.isMember(boardId, userId);
    }

    public boolean canMove(HubUserDetails user, Task task) {
        if (task.getBoard() != null) {
            return isBoardMember(task.getBoard().getId(), user.getId());
        }
        return canManageAll(user) || (canUseBoard(user) && !has(user, Role.HEAD)
                && task.getAssignee().getId().equals(user.getId()));
    }

    /** Создавать входящие задачи вручную. */
    public boolean canCreate(HubUserDetails user) {
        return has(user, Role.PRODUCT, Role.ADMIN, Role.SERVICE_CENTER);
    }

    /** Назначать входящую задачу любому исполнителю. */
    public boolean canAssignAny(HubUserDetails user) {
        return has(user, Role.PRODUCT, Role.ADMIN);
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

    /**
     * Ручная задача. На своей доске (boardId) создают участники доски, исполнитель выбирается среди участников.
     * Входящие: PRODUCT и ADMIN любому исполнителю, сервисный центр только себе.
     */
    @Transactional
    public Task createManual(TaskForm form, HubUserDetails user) {
        TaskBoard board = null;
        if (form.getBoardId() != null) {
            board = boards.findActiveWithMembers(form.getBoardId()).orElseThrow(() -> new TaskNotFoundException(form.getBoardId()));
            if (!board.hasMember(user.getId())) {
                throw new AccessDeniedException("Вы не участник этой доски");
            }
        } else if (!canCreate(user)) {
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
        Long assigneeId = board != null || canAssignAny(user) ? form.getAssigneeId() : user.getId();
        User assignee = assigneeId == null ? null : users.findById(assigneeId).filter(User::isActive).orElse(null);
        if (assignee == null || (board != null && !board.hasMember(assignee.getId()))) {
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
        t.setBoard(board);
        if (sku != null) {
            t.setSku(sku);
            t.setRelated(RelatedType.SKU, sku.getId(), sku.getModel(), "/sku/" + sku.getModel());
        }
        Task saved = repository.save(t);
        if (board != null) {
            activity.record(board.getId(), saved.getId(), user.getId(), BoardActivity.CARD_CREATED, title,
                    assignee.getFullName(), null);
        }
        return saved;
    }

    // ------------------------------------------------------------------ доска

    @Transactional(readOnly = true)
    public Board board(TaskFilter filter, HubUserDetails user) {
        return board(filter, user, false);
    }

    /** Входящие запросы (или их архив). */
    @Transactional(readOnly = true)
    public Board board(TaskFilter filter, HubUserDetails user, boolean archived) {
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
        List<Task> tasks = queries.board(null, archived, assigneeId, type, filter.sku(), filter.line(),
                LocalDateTime.now().minusDays(props.doneVisibleDays()));
        Map<TaskStatus, List<Task>> columns = new EnumMap<>(TaskStatus.class);
        for (TaskStatus s : TaskStatus.values()) {
            columns.put(s, new ArrayList<>());
        }
        for (Task t : tasks) {
            columns.get(t.getStatus()).add(t);
        }
        return new Board(columns, canManageAll(user), canSeeAll(user), null, null, Map.of(), Map.of(), !archived);
    }

    @Transactional(readOnly = true)
    public Task detail(Long id, HubUserDetails user) {
        Task t = repository.findDetailed(id).orElseThrow(() -> new TaskNotFoundException(id));
        if (t.getBoard() != null) {
            if (!isBoardMember(t.getBoard().getId(), user.getId())) {
                throw new AccessDeniedException("Задача с чужой доски");
            }
        } else if (!canSeeAll(user) && !t.getAssignee().getId().equals(user.getId())) {
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
        TaskStatus before = t.getStatus();
        t.moveTo(status);
        if (t.getBoard() != null && before != status) {
            var cols = t.getBoard().getColumns();
            activity.record(t.getBoard().getId(), t.getId(), user.getId(), BoardActivity.CARD_MOVED, t.getTitle(),
                    cols.containsKey(before) ? cols.get(before).getTitle() : before.name(),
                    cols.containsKey(status) ? cols.get(status).getTitle() : status.name(),
                    cols.containsKey(before) ? cols.get(before).getColor() : null,
                    cols.containsKey(status) ? cols.get(status).getColor() : null);
        }
        if (status == TaskStatus.IN_PROGRESS && t.getRelatedType() == RelatedType.QUESTION) {
            questions.markInProgress(t.getRelatedId());
        }
        return t;
    }

    /**
     * Правка названия и описания карточки своей доски (участниками доски). Системные входящие задачи так не правятся:
     * их название собирает приложение.
     */
    @Transactional
    public Task editCard(Long id, String title, String description, HubUserDetails user) {
        Task t = repository.findDetailed(id).orElseThrow(() -> new TaskNotFoundException(id));
        if (t.getBoard() == null || !isBoardMember(t.getBoard().getId(), user.getId())) {
            throw new AccessDeniedException("Править можно только карточки своих досок, где вы участник");
        }
        String newTitle = title == null ? "" : title.strip();
        if (newTitle.isEmpty()) {
            throw new FormValidationException(Map.of("title", "validation.required"));
        }
        if (newTitle.length() > 500) {
            newTitle = newTitle.substring(0, 500);
        }
        String newDescription = description == null || description.isBlank() ? null : description.strip();
        if (newDescription != null && newDescription.length() > 4000) {
            newDescription = newDescription.substring(0, 4000);
        }
        String oldTitle = t.getTitle();
        boolean titleChanged = !oldTitle.equals(newTitle);
        boolean descriptionChanged = !java.util.Objects.equals(t.getDescription(), newDescription);
        if (titleChanged) {
            t.setTitle(newTitle);
        }
        if (descriptionChanged) {
            t.setDescription(newDescription);
        }
        if (titleChanged || descriptionChanged) {
            t.touch();
        }
        Long boardId = t.getBoard().getId();
        if (titleChanged) {
            activity.record(boardId, t.getId(), user.getId(), BoardActivity.CARD_RENAMED, newTitle, oldTitle, newTitle);
        }
        if (descriptionChanged) {
            activity.record(boardId, t.getId(), user.getId(), BoardActivity.CARD_DESCRIBED, t.getTitle(), null, null);
        }
        return t;
    }

    /** Смена приоритета: тем, кто может двигать карточку. */
    @Transactional
    public Task setPriority(Long id, TaskPriority priority, HubUserDetails user) {
        Task t = repository.findDetailed(id).orElseThrow(() -> new TaskNotFoundException(id));
        if (!canMove(user, t)) {
            throw new AccessDeniedException("Нет прав менять эту задачу");
        }
        TaskPriority before = t.getPriority();
        if (before != priority) {
            t.setPriority(priority);
            t.touch();
            if (t.getBoard() != null) {
                activity.record(t.getBoard().getId(), t.getId(), user.getId(), BoardActivity.CARD_PRIORITY, t.getTitle(),
                        before.name(), priority.name());
            }
        }
        return t;
    }

    /** В архив уходят только закрытые задачи: сначала закройте, потом архивируйте. */
    @Transactional
    public void archive(Long id, HubUserDetails user) {
        Task t = repository.findDetailed(id).orElseThrow(() -> new TaskNotFoundException(id));
        if (!canMove(user, t)) {
            throw new AccessDeniedException("Нет прав менять эту задачу");
        }
        if (t.getStatus() != TaskStatus.DONE) {
            throw new TaskRuleException("tasks.rule.archiveDone");
        }
        t.archive();
        if (t.getBoard() != null) {
            activity.record(t.getBoard().getId(), t.getId(), user.getId(), BoardActivity.CARD_ARCHIVED, t.getTitle(), null, null);
        }
    }

    @Transactional
    public void unarchive(Long id, HubUserDetails user) {
        Task t = repository.findDetailed(id).orElseThrow(() -> new TaskNotFoundException(id));
        if (!canMove(user, t)) {
            throw new AccessDeniedException("Нет прав менять эту задачу");
        }
        t.unarchive();
        if (t.getBoard() != null) {
            activity.record(t.getBoard().getId(), t.getId(), user.getId(), BoardActivity.CARD_RESTORED, t.getTitle(), null, null);
        }
    }

    @Transactional
    public void reassign(Long id, Long assigneeId, HubUserDetails user) {
        Task t = repository.findDetailed(id).orElseThrow(() -> new TaskNotFoundException(id));
        if (t.getBoard() != null) {
            TaskBoard board = boards.findActiveWithMembers(t.getBoard().getId()).orElseThrow(() -> new TaskNotFoundException(id));
            if (!board.hasMember(user.getId()) || !board.hasMember(assigneeId)) {
                throw new AccessDeniedException("Исполнителем может быть только участник доски");
            }
        } else if (!canAssignAny(user) || !(canManageAll(user) || t.getAssignee().getId().equals(user.getId()))) {
            throw new AccessDeniedException("Переназначать входящие задачи могут PRODUCT (свои) и ADMIN");
        }
        User assignee = users.findById(assigneeId).filter(User::isActive).orElseThrow();
        boolean changed = !t.getAssignee().getId().equals(assignee.getId());
        t.setAssignee(assignee);
        if (t.getBoard() != null && changed) {
            activity.record(t.getBoard().getId(), t.getId(), user.getId(), BoardActivity.CARD_ASSIGNED, t.getTitle(),
                    assignee.getFullName(), null);
        }
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
        return repository.countNewInbox(userId, TaskStatus.NEW);
    }

    /** Пользователи, которых можно назначить исполнителем. */
    @Transactional(readOnly = true)
    public List<User> assignableUsers() {
        return Stream.concat(users.findActiveByRole(Role.PRODUCT).stream(), users.findActiveByRole(Role.SERVICE_CENTER).stream())
                .distinct().toList();
    }
}
