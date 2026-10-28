package ru.eltex.producthub.task.web;

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
import ru.eltex.producthub.task.service.BoardChatService;

/** Чат своих досок: сообщения доски и карточки отдаются кусками страницы (HTMX), опрос раз в несколько секунд. */
@Controller
public class ChatController {

    private final BoardChatService chat;

    public ChatController(BoardChatService chat) {
        this.chat = chat;
    }

    private String boardList(Long boardId, HubUserDetails user, Model model) {
        model.addAttribute("chatMessages", chat.boardMessages(boardId, user));
        model.addAttribute("chatUrl", "/boards/" + boardId + "/chat");
        model.addAttribute("chatId", "chat-board");
        return "tasks/chat :: list";
    }

    private String cardList(Long taskId, HubUserDetails user, Model model) {
        model.addAttribute("chatMessages", chat.cardMessages(taskId, user));
        model.addAttribute("chatUrl", "/tasks/" + taskId + "/chat");
        model.addAttribute("chatId", "chat-card");
        return "tasks/chat :: list";
    }

    @GetMapping("/boards/{id}/chat/messages")
    public String boardMessages(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user, Model model) {
        return boardList(id, user, model);
    }

    @PostMapping("/boards/{id}/chat")
    public String postBoard(@PathVariable Long id, @RequestParam String text,
                            @RequestHeader(value = "HX-Request", required = false) String htmx,
                            @AuthenticationPrincipal HubUserDetails user, Model model) {
        try {
            chat.post(id, null, text, user);
        } catch (FormValidationException ignored) {
            // пустое сообщение просто не отправляется
        }
        return htmx != null ? boardList(id, user, model) : "redirect:/boards/" + id + "/chat";
    }

    @GetMapping("/tasks/{id}/chat/messages")
    public String cardMessages(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user, Model model) {
        return cardList(id, user, model);
    }

    @PostMapping("/tasks/{id}/chat")
    public String postCard(@PathVariable Long id, @RequestParam String text,
                           @RequestHeader(value = "HX-Request", required = false) String htmx,
                           @AuthenticationPrincipal HubUserDetails user, Model model) {
        try {
            chat.post(null, id, text, user);
        } catch (FormValidationException ignored) {
            // пустое сообщение просто не отправляется
        }
        return htmx != null ? cardList(id, user, model) : "redirect:/tasks/" + id;
    }

    /** Мягкое удаление сообщения, ответ - обновленный чат, в котором оно было. */
    @PostMapping("/chat/{messageId}/delete")
    public String delete(@PathVariable Long messageId, @AuthenticationPrincipal HubUserDetails user, Model model) {
        var scope = chat.delete(messageId, user);
        return scope.taskId() == null ? boardList(scope.boardId(), user, model) : cardList(scope.taskId(), user, model);
    }
}
