package ru.eltex.producthub.task.web;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.qa.domain.QuestionKind;
import ru.eltex.producthub.qa.service.QuestionService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.sku.service.ReferenceDataService;
import ru.eltex.producthub.task.domain.RelatedType;
import ru.eltex.producthub.task.domain.Task;
import ru.eltex.producthub.task.domain.TaskPriority;
import ru.eltex.producthub.task.domain.TaskStatus;
import ru.eltex.producthub.task.domain.TaskType;
import ru.eltex.producthub.task.service.Board;
import ru.eltex.producthub.task.service.TaskFilter;
import ru.eltex.producthub.task.service.TaskForm;
import ru.eltex.producthub.task.service.TaskRuleException;
import ru.eltex.producthub.task.service.TaskService;

@Controller
public class TaskController {

    private final TaskService service;
    private final QuestionService questions;
    private final ReferenceDataService reference;
    private final MessageSource messages;

    public TaskController(TaskService service, QuestionService questions, ReferenceDataService reference,
                          MessageSource messages) {
        this.service = service;
        this.questions = questions;
        this.reference = reference;
        this.messages = messages;
    }

    @GetMapping("/tasks")
    public String board(@RequestParam(required = false) String assignee, @RequestParam(required = false) String type,
                        @RequestParam(required = false) String sku, @RequestParam(required = false) Long line,
                        @RequestHeader(value = "HX-Request", required = false) String htmx,
                        @AuthenticationPrincipal HubUserDetails user, Model model) {
        // SALES и RND доски не видят: у них "Мои вопросы"
        if (!service.canUseBoard(user)) {
            return "redirect:/questions/my";
        }
        TaskFilter filter = new TaskFilter(assignee, type, sku, line);
        Board board = service.board(filter, user);
        model.addAttribute("board", board);
        model.addAttribute("filter", filter);
        model.addAttribute("statuses", TaskStatus.values());
        model.addAttribute("movable", movable(board, user));
        model.addAttribute("today", java.time.LocalDate.now());
        model.addAttribute("activeMenu", "tasks");
        model.addAttribute("canBoard", true);
        model.addAttribute("canCreate", service.canCreate(user));
        if (htmx != null) {
            return "tasks/board :: board";
        }
        model.addAttribute("types", TaskType.values());
        model.addAttribute("lines", reference.lines());
        model.addAttribute("assignees", service.assignableUsers());
        return "tasks/board";
    }

    private Set<Long> movable(Board board, HubUserDetails user) {
        Set<Long> ids = new HashSet<>();
        board.columns().values().forEach(list -> list.stream().filter(t -> service.canMove(user, t))
                .forEach(t -> ids.add(t.getId())));
        return ids;
    }

    @GetMapping("/tasks/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user, Model model) {
        Task t = service.detail(id, user);
        model.addAttribute("t", t);
        model.addAttribute("statuses", TaskStatus.values());
        model.addAttribute("canMove", service.canMove(user, t));
        model.addAttribute("canAssign", service.canManageAll(user));
        model.addAttribute("assignees", service.assignableUsers());
        model.addAttribute("today", java.time.LocalDate.now());
        model.addAttribute("activeMenu", "tasks");
        model.addAttribute("canBoard", service.canUseBoard(user));
        if (t.getRelatedType() == RelatedType.QUESTION) {
            var q = questions.get(t.getRelatedId());
            model.addAttribute("question", q);
            model.addAttribute("canAnswer", questions.canAnswer(user, q));
            model.addAttribute("isEdit", q.getKind() == QuestionKind.EDIT_PROPOSAL);
        }
        return "tasks/detail";
    }

    /** Смена колонки перетаскиванием. 204 - готово, 409 - правило не позволяет, текст ошибки в теле. */
    @PostMapping("/tasks/{id}/status")
    public ResponseEntity<String> status(@PathVariable Long id, @RequestParam TaskStatus status,
                                         @AuthenticationPrincipal HubUserDetails user) {
        try {
            service.move(id, status, user);
            return ResponseEntity.noContent().build();
        } catch (TaskRuleException e) {
            return ResponseEntity.status(409).body(messages.getMessage(e.getMessageKey(), null, Locale.forLanguageTag("ru")));
        }
    }

    /** То же, но со страницы задачи (обычная форма). */
    @PostMapping("/tasks/{id}/move")
    public String move(@PathVariable Long id, @RequestParam TaskStatus status,
                       @AuthenticationPrincipal HubUserDetails user, RedirectAttributes redirect) {
        try {
            service.move(id, status, user);
        } catch (TaskRuleException e) {
            redirect.addFlashAttribute("ruleError", e.getMessageKey());
        }
        return "redirect:/tasks/" + id;
    }

    @PostMapping("/tasks/{id}/assign")
    public String assign(@PathVariable Long id, @RequestParam Long assigneeId,
                         @AuthenticationPrincipal HubUserDetails user) {
        service.reassign(id, assigneeId, user);
        return "redirect:/tasks/" + id;
    }

    @GetMapping("/tasks/new")
    public String newForm(@AuthenticationPrincipal HubUserDetails user, Model model) {
        if (!service.canCreate(user)) {
            throw new org.springframework.security.access.AccessDeniedException("Нет прав на создание задач");
        }
        TaskForm form = new TaskForm();
        form.setAssigneeId(user.getId());
        form.setDueDate(java.time.LocalDate.now().plusDays(7).toString());
        fillForm(model, user);
        model.addAttribute("form", form);
        return "tasks/form";
    }

    @PostMapping("/tasks")
    public String create(@ModelAttribute("form") TaskForm form, @AuthenticationPrincipal HubUserDetails user,
                         Model model) {
        try {
            return "redirect:/tasks/" + service.createManual(form, user).getId();
        } catch (FormValidationException e) {
            model.addAttribute("errors", e.getErrors());
            fillForm(model, user);
            return "tasks/form";
        }
    }

    private void fillForm(Model model, HubUserDetails user) {
        model.addAttribute("types", TaskType.values());
        model.addAttribute("priorities", TaskPriority.values());
        model.addAttribute("assignees", service.assignableUsers());
        model.addAttribute("canPickAssignee", service.canManageAll(user));
        model.addAttribute("activeMenu", "tasks");
        model.addAttribute("canBoard", service.canUseBoard(user));
    }
}
