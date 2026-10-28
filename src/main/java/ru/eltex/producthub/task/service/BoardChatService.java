package ru.eltex.producthub.task.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.UserRepository;
import ru.eltex.producthub.task.domain.Task;
import ru.eltex.producthub.task.domain.TaskMessage;
import ru.eltex.producthub.task.repo.TaskBoardRepository;
import ru.eltex.producthub.task.repo.TaskMessageRepository;
import ru.eltex.producthub.task.repo.TaskRepository;

/**
 * Чат своих досок: общий чат доски и обсуждение внутри карточки. Читают и пишут только участники доски.
 * На системной доске "Входящие запросы" чата нет. Удалить сообщение может автор или владелец доски (мягко).
 */
@Service
public class BoardChatService {

    public static final int MAX_LENGTH = 2000;
    private static final int LIMIT = 200;

    private final TaskMessageRepository messages;
    private final TaskBoardRepository boards;
    private final TaskRepository tasks;
    private final UserRepository users;

    public BoardChatService(TaskMessageRepository messages, TaskBoardRepository boards, TaskRepository tasks,
                            UserRepository users) {
        this.messages = messages;
        this.boards = boards;
        this.tasks = tasks;
        this.users = users;
    }

    public record ChatMessage(Long id, Long authorId, String author, String body, LocalDateTime at, boolean mine,
                              boolean canDelete) {
    }

    /** Где находится сообщение: нужно, чтобы после удаления показать нужный чат. */
    public record Scope(Long boardId, Long taskId) {
    }

    private void requireMember(Long boardId, HubUserDetails user) {
        if (!boards.isMember(boardId, user.getId())) {
            throw new AccessDeniedException("Вы не участник этой доски");
        }
    }

    private Long boardOfTask(Long taskId, HubUserDetails user) {
        Task t = tasks.findDetailed(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
        if (t.getBoard() == null) {
            throw new AccessDeniedException("Чат есть только у карточек своих досок");
        }
        requireMember(t.getBoard().getId(), user);
        return t.getBoard().getId();
    }

    private List<ChatMessage> toView(List<TaskMessage> newestFirst, Long boardId, HubUserDetails user) {
        boolean owner = boards.isOwner(boardId, user.getId());
        List<TaskMessage> ordered = new ArrayList<>(newestFirst);
        Collections.reverse(ordered);
        return ordered.stream().map(m -> {
            boolean mine = m.getAuthor().getId().equals(user.getId());
            return new ChatMessage(m.getId(), m.getAuthor().getId(), m.getAuthor().getFullName(), m.getBody(),
                    m.getCreatedAt(), mine, mine || owner);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> boardMessages(Long boardId, HubUserDetails user) {
        requireMember(boardId, user);
        return toView(messages.boardChat(boardId, PageRequest.of(0, LIMIT)), boardId, user);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> cardMessages(Long taskId, HubUserDetails user) {
        Long boardId = boardOfTask(taskId, user);
        return toView(messages.cardChat(taskId, PageRequest.of(0, LIMIT)), boardId, user);
    }

    /** Новое сообщение в чат доски (taskId пусто) или в обсуждение карточки. */
    @Transactional
    public void post(Long boardId, Long taskId, String text, HubUserDetails user) {
        Long board = taskId == null ? boardId : boardOfTask(taskId, user);
        requireMember(board, user);
        String body = text == null ? "" : text.strip();
        if (body.isEmpty()) {
            Map<String, String> errors = new LinkedHashMap<>();
            errors.put("text", "validation.required");
            throw new FormValidationException(errors);
        }
        if (body.length() > MAX_LENGTH) {
            body = body.substring(0, MAX_LENGTH);
        }
        messages.save(new TaskMessage(board, taskId, users.getReferenceById(user.getId()), body));
    }

    /** Мягкое удаление; возвращает, из какого чата сообщение, чтобы его перерисовать. */
    @Transactional
    public Scope delete(Long messageId, HubUserDetails user) {
        TaskMessage m = messages.findWithAuthor(messageId).orElseThrow(() -> new TaskNotFoundException(messageId));
        requireMember(m.getBoardId(), user);
        if (!m.getAuthor().getId().equals(user.getId()) && !boards.isOwner(m.getBoardId(), user.getId())) {
            throw new AccessDeniedException("Удалить сообщение может автор или владелец доски");
        }
        m.markDeleted();
        return new Scope(m.getBoardId(), m.getTaskId());
    }
}
