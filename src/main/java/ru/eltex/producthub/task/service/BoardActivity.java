package ru.eltex.producthub.task.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.User;
import ru.eltex.producthub.security.UserRepository;
import ru.eltex.producthub.task.domain.Task;
import ru.eltex.producthub.task.domain.TaskEvent;
import ru.eltex.producthub.task.repo.TaskBoardRepository;
import ru.eltex.producthub.task.repo.TaskEventRepository;
import ru.eltex.producthub.task.repo.TaskRepository;

/** Журнал изменений своих досок: пишется сервисами, читают участники доски (в журнал карточки - те же). */
@Service
public class BoardActivity {

    public static final String BOARD_CREATED = "BOARD_CREATED";
    public static final String BOARD_RENAMED = "BOARD_RENAMED";
    public static final String COLUMNS_CHANGED = "COLUMNS_CHANGED";
    public static final String MEMBER_ADDED = "MEMBER_ADDED";
    public static final String MEMBER_REMOVED = "MEMBER_REMOVED";
    public static final String CARD_CREATED = "CARD_CREATED";
    public static final String CARD_MOVED = "CARD_MOVED";
    public static final String CARD_ASSIGNED = "CARD_ASSIGNED";
    public static final String CARD_ARCHIVED = "CARD_ARCHIVED";
    public static final String CARD_RESTORED = "CARD_RESTORED";
    public static final String CARD_RENAMED = "CARD_RENAMED";
    public static final String CARD_DESCRIBED = "CARD_DESCRIBED";
    public static final String CARD_PRIORITY = "CARD_PRIORITY";

    private static final int LIMIT = 300;

    private final TaskEventRepository events;
    private final TaskBoardRepository boards;
    private final TaskRepository tasks;
    private final UserRepository users;

    public BoardActivity(TaskEventRepository events, TaskBoardRepository boards, TaskRepository tasks, UserRepository users) {
        this.events = events;
        this.boards = boards;
        this.tasks = tasks;
        this.users = users;
    }

    /** Запись события. Вызывается внутри транзакции действия, чтобы журнал не расходился с данными. */
    public void record(Long boardId, Long taskId, Long actorId, String kind, String subject, String arg1, String arg2) {
        User actor = users.getReferenceById(actorId);
        events.save(new TaskEvent(boardId, taskId, actor, kind, subject, arg1, arg2));
    }

    /** То же, что record, но с цветами колонок (для перемещений карточек). */
    public void record(Long boardId, Long taskId, Long actorId, String kind, String subject, String arg1, String arg2,
                       String color1, String color2) {
        User actor = users.getReferenceById(actorId);
        events.save(new TaskEvent(boardId, taskId, actor, kind, subject, arg1, arg2, color1, color2));
    }

    public record EventView(Long id, String actor, String kind, String subject, String arg1, String arg2,
                            LocalDateTime at, Long taskId, String color1, String color2) {
    }

    /** Цвет колонки берется из записи; у старых записей (без цвета) - по названию из текущих колонок доски. */
    private static EventView view(TaskEvent e, Map<String, String> colorByTitle) {
        String c1 = e.getColor1() != null ? e.getColor1() : colorByTitle.getOrDefault(e.getArg1(), "");
        String c2 = e.getColor2() != null ? e.getColor2() : colorByTitle.getOrDefault(e.getArg2(), "");
        if (!BoardActivity.CARD_MOVED.equals(e.getKind())) {
            c1 = "";
            c2 = "";
        }
        return new EventView(e.getId(), e.getActor().getFullName(), e.getKind(), e.getSubject() == null ? "" : e.getSubject(),
                e.getArg1() == null ? "" : e.getArg1(), e.getArg2() == null ? "" : e.getArg2(), e.getCreatedAt(),
                e.getTaskId(), c1, c2);
    }

    private Map<String, String> colorsOf(Long boardId) {
        Map<String, String> byTitle = new HashMap<>();
        boards.findById(boardId).ifPresent(b -> b.getColumns().values().forEach(c -> byTitle.put(c.getTitle(), c.getColor())));
        return byTitle;
    }

    @Transactional(readOnly = true)
    public List<EventView> forBoard(Long boardId, HubUserDetails user) {
        if (!boards.isMember(boardId, user.getId())) {
            throw new AccessDeniedException("Вы не участник этой доски");
        }
        Map<String, String> colors = colorsOf(boardId);
        return events.forBoard(boardId, PageRequest.of(0, LIMIT)).stream().map(e -> view(e, colors)).toList();
    }

    @Transactional(readOnly = true)
    public List<EventView> forTask(Long taskId, HubUserDetails user) {
        Task t = tasks.findDetailed(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
        if (t.getBoard() == null || !boards.isMember(t.getBoard().getId(), user.getId())) {
            throw new AccessDeniedException("Журнал есть только у карточек своих досок");
        }
        Map<String, String> colors = colorsOf(t.getBoard().getId());
        return events.forTask(taskId, PageRequest.of(0, LIMIT)).stream().map(e -> view(e, colors)).toList();
    }
}
