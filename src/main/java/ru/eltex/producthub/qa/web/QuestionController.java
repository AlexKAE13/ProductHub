package ru.eltex.producthub.qa.web;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.qa.domain.QuestionKind;
import ru.eltex.producthub.qa.domain.TargetType;
import ru.eltex.producthub.qa.service.QuestionService;
import ru.eltex.producthub.security.HubUserDetails;

@Controller
public class QuestionController {

    private final QuestionService service;

    private final ru.eltex.producthub.task.service.BoardService boards;
    private final ru.eltex.producthub.task.service.TaskService tasks;

    public QuestionController(QuestionService service, ru.eltex.producthub.task.service.BoardService boards,
                              ru.eltex.producthub.task.service.TaskService tasks) {
        this.boards = boards;
        this.tasks = tasks;
        this.service = service;
    }

    /** Новый вопрос или правка из блока на странице объекта (HTMX): возвращает обновленный блок. */
    @PostMapping("/questions")
    public String ask(@RequestParam TargetType targetType, @RequestParam Long targetId,
                      @RequestParam(defaultValue = "QUESTION") QuestionKind kind, @RequestParam(required = false) String text,
                      @RequestParam(defaultValue = "false") boolean technical,
                      @AuthenticationPrincipal HubUserDetails user, Model model) {
        try {
            service.ask(kind, targetType, targetId, text, technical, user);
            model.addAttribute("qaMessage", kind == QuestionKind.QUESTION ? "qa.sent.question" : "qa.sent.edit");
        } catch (FormValidationException e) {
            model.addAttribute("qaError", e.getErrors().values().iterator().next());
            model.addAttribute("qaText", text);
        }
        model.addAttribute("qa", service.view(targetType, targetId, user));
        return "qa/block :: block";
    }

    /** "Возможно, ответ уже есть": подсказки, пока человек набирает вопрос. */
    @GetMapping("/questions/similar")
    public String similar(@RequestParam TargetType targetType, @RequestParam Long targetId,
                          @RequestParam(required = false) String text, @AuthenticationPrincipal HubUserDetails user,
                          Model model) {
        model.addAttribute("similar", service.similar(text, targetType, targetId, user));
        return "qa/block :: similar";
    }

    @GetMapping("/questions/my")
    public String my(@AuthenticationPrincipal HubUserDetails user, Model model) {
        model.addAttribute("questions", service.myQuestions(user));
        model.addAttribute("activeMenu", "tasks");
        model.addAttribute("canBoard", false);
        // общие вкладки раздела "Задачи": входящие, свои доски, мои вопросы
        model.addAttribute("tab", "questions");
        model.addAttribute("tabBoards", boards.mine(user));
        model.addAttribute("canInbox", tasks.canUseBoard(user));
        return "qa/my";
    }

    @GetMapping("/questions/open")
    public String open(@RequestParam TargetType type, @RequestParam Long id) {
        return "redirect:" + service.targetUrl(type, id).replace(" ", "%20");
    }

    @PostMapping("/questions/{id}/answer")
    public String answer(@PathVariable Long id, @RequestParam(required = false) String answer,
                         @RequestParam(defaultValue = "false") boolean publishToFaq,
                         @RequestParam(required = false) String back, @AuthenticationPrincipal HubUserDetails user,
                         RedirectAttributes redirect) {
        try {
            service.answer(id, answer, publishToFaq, user);
        } catch (FormValidationException e) {
            redirect.addFlashAttribute("qaError", e.getErrors().values().iterator().next());
        }
        return "redirect:" + safe(back);
    }

    @PostMapping("/questions/{id}/accept")
    public String accept(@PathVariable Long id, @RequestParam(required = false) String comment,
                         @RequestParam(required = false) String back, @AuthenticationPrincipal HubUserDetails user,
                         RedirectAttributes redirect) {
        return resolveEdit(id, true, comment, back, user, redirect);
    }

    @PostMapping("/questions/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String comment,
                         @RequestParam(required = false) String back, @AuthenticationPrincipal HubUserDetails user,
                         RedirectAttributes redirect) {
        return resolveEdit(id, false, comment, back, user, redirect);
    }

    private String resolveEdit(Long id, boolean accept, String comment, String back, HubUserDetails user,
                               RedirectAttributes redirect) {
        try {
            service.resolveEdit(id, accept, comment, user);
        } catch (FormValidationException e) {
            redirect.addFlashAttribute("qaError", e.getErrors().values().iterator().next());
        }
        return "redirect:" + safe(back);
    }

    @PostMapping("/questions/{id}/close")
    public String close(@PathVariable Long id, @RequestParam(required = false) String back,
                        @AuthenticationPrincipal HubUserDetails user) {
        service.close(id, user);
        return "redirect:" + safe(back == null ? "/questions/my" : back);
    }

    /** Возврат только на страницы этого же приложения. */
    static String safe(String back) {
        if (back == null || !back.startsWith("/") || back.startsWith("//") || back.contains("\\")) {
            return "/questions/my";
        }
        return back;
    }

    @SuppressWarnings("unused")
    private static List<String> none() {
        return List.of();
    }

    @SuppressWarnings("unused")
    private static Map<String, String> noneMap() {
        return Map.of();
    }
}
