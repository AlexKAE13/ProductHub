package ru.eltex.producthub.task.web;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.task.domain.BoardColor;
import ru.eltex.producthub.task.domain.TaskPriority;
import ru.eltex.producthub.task.domain.TaskStatus;
import ru.eltex.producthub.task.service.Board;
import ru.eltex.producthub.task.service.BoardActivity;
import ru.eltex.producthub.task.service.BoardChatService;
import ru.eltex.producthub.task.service.BoardInfo;
import ru.eltex.producthub.task.service.BoardService;
import ru.eltex.producthub.task.service.TaskFilter;
import ru.eltex.producthub.task.service.TaskForm;
import ru.eltex.producthub.task.service.TaskService;

/** Свои доски задач: список, создание, доска и ее архив, настройки, участники. */
@Controller
public class BoardController {

    private final BoardService boards;
    private final TaskService tasks;
    private final BoardChatService chat;
    private final BoardActivity activity;

    public BoardController(BoardService boards, TaskService tasks, BoardChatService chat, BoardActivity activity) {
        this.boards = boards;
        this.tasks = tasks;
        this.chat = chat;
        this.activity = activity;
    }

    private void tabs(Model model, HubUserDetails user, String tab, Long boardId) {
        model.addAttribute("tabBoards", boards.mine(user));
        model.addAttribute("canInbox", tasks.canUseBoard(user));
        model.addAttribute("tab", tab);
        model.addAttribute("tabBoardId", boardId);
        model.addAttribute("activeMenu", "tasks");
        model.addAttribute("canBoard", tasks.canUseBoard(user));
    }

    @GetMapping("/boards")
    public String list(@AuthenticationPrincipal HubUserDetails user, Model model) {
        tabs(model, user, "boards", null);
        model.addAttribute("boardsList", boards.mine(user));
        return "tasks/boards";
    }

    @PostMapping("/boards")
    public String create(@RequestParam String name, @RequestParam(required = false) String description,
                         @AuthenticationPrincipal HubUserDetails user, Model model) {
        try {
            return "redirect:/boards/" + boards.create(name, description, user);
        } catch (FormValidationException e) {
            tabs(model, user, "boards", null);
            model.addAttribute("boardsList", boards.mine(user));
            model.addAttribute("errors", e.getErrors());
            model.addAttribute("name", name);
            model.addAttribute("description", description);
            return "tasks/boards";
        }
    }

    @GetMapping("/boards/{id}")
    public String board(@PathVariable Long id, @RequestParam(required = false) String assignee,
                        @RequestHeader(value = "HX-Request", required = false) String htmx,
                        @AuthenticationPrincipal HubUserDetails user, Model model) {
        return show(id, assignee, htmx, user, model, false);
    }

    @GetMapping("/boards/{id}/archive")
    public String archive(@PathVariable Long id, @RequestParam(required = false) String assignee,
                          @RequestHeader(value = "HX-Request", required = false) String htmx,
                          @AuthenticationPrincipal HubUserDetails user, Model model) {
        return show(id, assignee, htmx, user, model, true);
    }

    private String show(Long id, String assignee, String htmx, HubUserDetails user, Model model, boolean archived) {
        TaskFilter filter = new TaskFilter(assignee, null, null, null);
        Board board = boards.view(id, filter, archived, user);
        BoardInfo info = boards.info(id, user);
        model.addAttribute("board", board);
        model.addAttribute("boardInfo", info);
        model.addAttribute("filter", filter);
        model.addAttribute("statuses", TaskStatus.values());
        Set<Long> movable = new HashSet<>();
        board.columns().values().forEach(list -> list.forEach(t -> movable.add(t.getId())));
        model.addAttribute("movable", movable);
        model.addAttribute("today", java.time.LocalDate.now());
        model.addAttribute("canCreate", true);
        model.addAttribute("base", "/boards/" + id + (archived ? "/archive" : ""));
        model.addAttribute("inbox", false);
        model.addAttribute("boardNav", archived ? "archive" : "board");
        model.addAttribute("archiveView", archived);
        tabs(model, user, archived ? "board-archive" : "board", id);
        if (htmx != null) {
            return "tasks/board :: board";
        }
        model.addAttribute("assignees", info.members());
        return "tasks/board";
    }

    /** Чат доски. */
    @GetMapping("/boards/{id}/chat")
    public String chatPage(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user, Model model) {
        BoardInfo info = boards.info(id, user);
        tabs(model, user, "board", id);
        model.addAttribute("boardInfo", info);
        model.addAttribute("boardNav", "chat");
        model.addAttribute("chatMessages", chat.boardMessages(id, user));
        model.addAttribute("chatUrl", "/boards/" + id + "/chat");
        model.addAttribute("chatId", "chat-board");
        return "tasks/board-chat";
    }

    /** Журнал изменений доски. */
    @GetMapping("/boards/{id}/log")
    public String logPage(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user, Model model) {
        BoardInfo info = boards.info(id, user);
        tabs(model, user, "board", id);
        model.addAttribute("boardInfo", info);
        model.addAttribute("boardNav", "log");
        model.addAttribute("events", activity.forBoard(id, user));
        return "tasks/board-log";
    }

    @GetMapping("/boards/{id}/cards/new")
    public String newCard(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user, Model model) {
        BoardInfo info = boards.info(id, user);
        TaskForm form = new TaskForm();
        form.setBoardId(id);
        form.setAssigneeId(user.getId());
        form.setDueDate(java.time.LocalDate.now().plusDays(7).toString());
        model.addAttribute("form", form);
        model.addAttribute("priorities", TaskPriority.values());
        model.addAttribute("activeMenu", "tasks");
        model.addAttribute("canBoard", tasks.canUseBoard(user));
        TaskController.boardForm(model, info);
        return "tasks/form";
    }

    // ------------------------------------------------------------------ настройки (владелец)

    @GetMapping("/boards/{id}/settings")
    public String settings(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user, Model model) {
        BoardInfo info = boards.info(id, user);
        fillSettings(model, user, info);
        return "tasks/board-settings";
    }

    private void fillSettings(Model model, HubUserDetails user, BoardInfo info) {
        tabs(model, user, "board", info.id());
        model.addAttribute("boardNav", "settings");
        model.addAttribute("boardInfo", info);
        model.addAttribute("statuses", TaskStatus.values());
        model.addAttribute("palette", BoardColor.KEYS);
        model.addAttribute("candidates", info.owner() ? boards.candidates(info.id(), user) : java.util.List.of());
    }

    @PostMapping("/boards/{id}/settings")
    public String saveSettings(@PathVariable Long id, @RequestParam String name,
                               @RequestParam(required = false) String description,
                               @RequestParam Map<String, String> params,
                               @AuthenticationPrincipal HubUserDetails user, Model model) {
        Map<TaskStatus, String> titles = new EnumMap<>(TaskStatus.class);
        Map<TaskStatus, String> colors = new EnumMap<>(TaskStatus.class);
        for (TaskStatus s : TaskStatus.values()) {
            titles.put(s, params.get("title." + s));
            colors.put(s, params.get("color." + s));
        }
        try {
            boards.update(id, name, description, titles, colors, user);
            return "redirect:/boards/" + id + "/settings?saved";
        } catch (FormValidationException e) {
            fillSettings(model, user, boards.info(id, user));
            model.addAttribute("errors", e.getErrors());
            return "tasks/board-settings";
        }
    }

    @PostMapping("/boards/{id}/members")
    public String addMember(@PathVariable Long id, @RequestParam Long userId, @AuthenticationPrincipal HubUserDetails user) {
        boards.addMember(id, userId, user);
        return "redirect:/boards/" + id + "/settings";
    }

    @PostMapping("/boards/{id}/members/{userId}/remove")
    public String removeMember(@PathVariable Long id, @PathVariable Long userId,
                               @AuthenticationPrincipal HubUserDetails user) {
        boards.removeMember(id, userId, user);
        return "redirect:/boards/" + id + "/settings";
    }

    @PostMapping("/boards/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user) {
        boards.delete(id, user);
        return "redirect:/boards";
    }
}
