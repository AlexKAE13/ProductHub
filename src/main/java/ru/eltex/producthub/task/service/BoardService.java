package ru.eltex.producthub.task.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.Role;
import ru.eltex.producthub.security.User;
import ru.eltex.producthub.security.UserRepository;
import ru.eltex.producthub.task.domain.BoardColor;
import ru.eltex.producthub.task.domain.ColumnStyle;
import ru.eltex.producthub.task.domain.Task;
import ru.eltex.producthub.task.domain.TaskBoard;
import ru.eltex.producthub.task.domain.TaskStatus;
import ru.eltex.producthub.task.domain.TaskType;
import ru.eltex.producthub.task.repo.TaskBoardRepository;
import ru.eltex.producthub.task.repo.TaskQueryRepository;
import ru.eltex.producthub.task.repo.TaskRepository;

/**
 * Свои доски задач: создает любой внутренний пользователь, владелец приглашает участников и настраивает названия и
 * цвета колонок. Участники видят доску, заводят, двигают и архивируют карточки. Правит доску только владелец.
 */
@Service
public class BoardService {

    private static final Locale RU = Locale.forLanguageTag("ru");

    private final TaskBoardRepository boards;
    private final TaskRepository tasks;
    private final TaskQueryRepository queries;
    private final UserRepository users;
    private final MessageSource messages;

    public BoardService(TaskBoardRepository boards, TaskRepository tasks, TaskQueryRepository queries,
                        UserRepository users, MessageSource messages) {
        this.boards = boards;
        this.tasks = tasks;
        this.queries = queries;
        this.users = users;
        this.messages = messages;
    }

    private static boolean internal(HubUserDetails user) {
        for (Role r : user.getRoles()) {
            if (r != Role.PARTNER) {
                return true;
            }
        }
        return false;
    }

    public boolean canUse(HubUserDetails user) {
        return internal(user);
    }

    private TaskBoard load(Long id, HubUserDetails user) {
        TaskBoard b = boards.findActiveWithMembers(id).orElseThrow(() -> new BoardNotFoundException(id));
        if (!internal(user) || !b.hasMember(user.getId())) {
            throw new AccessDeniedException("Вы не участник этой доски");
        }
        return b;
    }

    private TaskBoard loadOwned(Long id, HubUserDetails user) {
        TaskBoard b = load(id, user);
        if (!b.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Доску правит только владелец");
        }
        return b;
    }

    // ------------------------------------------------------------------ список и создание

    @Transactional(readOnly = true)
    public List<BoardInfo> mine(HubUserDetails user) {
        if (!internal(user)) {
            return List.of();
        }
        List<BoardInfo> result = new ArrayList<>();
        for (TaskBoard b : boards.findForMember(user.getId())) {
            result.add(new BoardInfo(b.getId(), b.getName(), b.getDescription(), b.getOwner().getId(),
                    b.getOwner().getFullName(), List.of(), Map.of(), Map.of(), b.getOwner().getId().equals(user.getId())));
        }
        return result;
    }

    @Transactional
    public Long create(String name, String description, HubUserDetails user) {
        if (!internal(user)) {
            throw new AccessDeniedException("Свои доски доступны внутренним пользователям");
        }
        String title = clean(name);
        if (title.isEmpty()) {
            throw new FormValidationException(Map.of("name", "validation.required"));
        }
        User owner = users.findById(user.getId()).orElseThrow();
        TaskBoard b = new TaskBoard(title, blankToNull(description), owner);
        for (TaskStatus s : TaskStatus.values()) {
            b.getColumns().put(s, new ColumnStyle(messages.getMessage("taskStatus." + s, null, RU), BoardColor.defaultFor(s)));
        }
        return boards.save(b).getId();
    }

    // ------------------------------------------------------------------ чтение

    @Transactional(readOnly = true)
    public BoardInfo info(Long id, HubUserDetails user) {
        TaskBoard b = load(id, user);
        List<BoardInfo.Member> members = b.getMembers().stream()
                .sorted((x, y) -> x.getFullName().compareToIgnoreCase(y.getFullName()))
                .map(u -> new BoardInfo.Member(u.getId(), u.getFullName(), u.getId().equals(b.getOwner().getId()))).toList();
        Map<TaskStatus, String> titles = new EnumMap<>(TaskStatus.class);
        Map<TaskStatus, String> colors = new EnumMap<>(TaskStatus.class);
        b.getColumns().forEach((s, c) -> {
            titles.put(s, c.getTitle());
            colors.put(s, c.getColor());
        });
        return new BoardInfo(b.getId(), b.getName(), b.getDescription(), b.getOwner().getId(), b.getOwner().getFullName(),
                members, titles, colors, b.getOwner().getId().equals(user.getId()));
    }

    /** Карточки доски по колонкам (или ее архив). assignee: пусто или "all" - все участники, "me" - свои, иначе id. */
    @Transactional(readOnly = true)
    public Board view(Long id, TaskFilter filter, boolean archived, HubUserDetails user) {
        BoardInfo info = info(id, user);
        Long assigneeId = null;
        if ("me".equals(filter.assignee())) {
            assigneeId = user.getId();
        } else if (filter.assignee() != null && !filter.assignee().isBlank() && !"all".equals(filter.assignee())) {
            try {
                assigneeId = Long.valueOf(filter.assignee());
            } catch (NumberFormatException ignored) {
                // неизвестное значение - показываем всех
            }
        }
        TaskType type = null;
        try {
            type = filter.type() == null || filter.type().isBlank() ? null : TaskType.valueOf(filter.type());
        } catch (IllegalArgumentException ignored) {
            // неизвестный тип игнорируем
        }
        List<Task> found = queries.board(id, archived, assigneeId, type, filter.sku(), filter.line(), (LocalDateTime) null);
        Map<TaskStatus, List<Task>> columns = new EnumMap<>(TaskStatus.class);
        for (TaskStatus s : TaskStatus.values()) {
            columns.put(s, new ArrayList<>());
        }
        found.forEach(t -> columns.get(t.getStatus()).add(t));
        return new Board(columns, true, true, id, info.name(), info.titles(), info.colors(), !archived);
    }

    /** Кого можно пригласить: активные внутренние пользователи, которых еще нет на доске. */
    @Transactional(readOnly = true)
    public List<User> candidates(Long id, HubUserDetails user) {
        TaskBoard b = loadOwned(id, user);
        return users.findAllByActiveTrueOrderByIdAsc().stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> r != Role.PARTNER))
                .filter(u -> !b.hasMember(u.getId()))
                .sorted((x, y) -> x.getFullName().compareToIgnoreCase(y.getFullName())).toList();
    }

    // ------------------------------------------------------------------ правка (только владелец)

    @Transactional
    public void update(Long id, String name, String description, Map<TaskStatus, String> titles,
                       Map<TaskStatus, String> colors, HubUserDetails user) {
        TaskBoard b = loadOwned(id, user);
        Map<String, String> errors = new LinkedHashMap<>();
        String title = clean(name);
        if (title.isEmpty()) {
            errors.put("name", "validation.required");
        }
        Map<TaskStatus, ColumnStyle> styles = new EnumMap<>(TaskStatus.class);
        for (TaskStatus s : TaskStatus.values()) {
            String t = clean(titles.get(s));
            String c = colors.get(s);
            if (t.isEmpty()) {
                errors.put("title." + s, "validation.required");
            }
            if (c == null || !BoardColor.KEYS.contains(c)) {
                c = BoardColor.defaultFor(s);
            }
            styles.put(s, new ColumnStyle(t.length() > 60 ? t.substring(0, 60) : t, c));
        }
        if (!errors.isEmpty()) {
            throw new FormValidationException(errors);
        }
        b.setName(title.length() > 120 ? title.substring(0, 120) : title);
        b.setDescription(blankToNull(description));
        b.getColumns().clear();
        b.getColumns().putAll(styles);
    }

    @Transactional
    public void addMember(Long id, Long userId, HubUserDetails user) {
        TaskBoard b = loadOwned(id, user);
        User u = users.findById(userId).filter(User::isActive).orElseThrow(() -> new AccessDeniedException("Пользователь не найден"));
        if (u.getRoles().stream().noneMatch(r -> r != Role.PARTNER)) {
            throw new AccessDeniedException("Во внутренние доски приглашают только внутренних пользователей");
        }
        b.getMembers().add(u);
    }

    /** Убирает участника; его карточки на этой доске переходят владельцу. Владельца убрать нельзя. */
    @Transactional
    public void removeMember(Long id, Long userId, HubUserDetails user) {
        TaskBoard b = loadOwned(id, user);
        if (b.getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("Владельца доски убрать нельзя");
        }
        b.getMembers().removeIf(u -> u.getId().equals(userId));
        tasks.findOnBoardAssignedTo(id, userId).forEach(t -> t.setAssignee(b.getOwner()));
    }

    /** Мягкое удаление: доска исчезает у всех, карточки остаются в базе. */
    @Transactional
    public void delete(Long id, HubUserDetails user) {
        loadOwned(id, user).markDeleted();
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static String blankToNull(String s) {
        String c = clean(s);
        if (c.isEmpty()) {
            return null;
        }
        return c.length() > 500 ? c.substring(0, 500) : c;
    }
}
