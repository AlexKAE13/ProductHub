package ru.eltex.producthub.task;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;
import ru.eltex.producthub.task.domain.Task;
import ru.eltex.producthub.task.domain.TaskStatus;
import ru.eltex.producthub.task.service.BoardActivity;
import ru.eltex.producthub.task.service.BoardChatService;
import ru.eltex.producthub.task.service.BoardService;
import ru.eltex.producthub.task.service.TaskForm;
import ru.eltex.producthub.task.service.TaskService;

/** Чат доски и карточки и журнал изменений: только для своих досок, только для участников. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class BoardChatTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    BoardService boards;
    @Autowired
    BoardChatService chat;
    @Autowired
    BoardActivity activity;
    @Autowired
    TaskService tasks;
    @Autowired
    HubUserDetailsService users;

    HubUserDetails sales;
    HubUserDetails rnd;
    HubUserDetails head;
    HubUserDetails product;

    @BeforeEach
    void setUp() {
        sales = users.loadUserByUsername("sales");
        rnd = users.loadUserByUsername("rnd");
        head = users.loadUserByUsername("head");
        product = users.loadUserByUsername("product");
    }

    private static String marker() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Long boardWithRnd() {
        Long id = boards.create("Чат-доска " + marker(), null, sales);
        boards.addMember(id, rnd.getId(), sales);
        return id;
    }

    private Task card(Long boardId, HubUserDetails author, Long assignee) {
        TaskForm f = new TaskForm();
        f.setTitle("Карточка " + marker());
        f.setBoardId(boardId);
        f.setAssigneeId(assignee);
        return tasks.createManual(f, author);
    }

    private List<String> kinds(Long boardId) {
        return activity.forBoard(boardId, sales).stream().map(BoardActivity.EventView::kind).toList();
    }

    // ---------------------------------------------------------- чат доски

    @Test
    void boardChatIsForMembersOnlyAndOldestFirst() {
        Long id = boardWithRnd();
        chat.post(id, null, "Первое", sales);
        chat.post(id, null, "  Второе  ", rnd);
        var list = chat.boardMessages(id, sales);
        assertEquals(List.of("Первое", "Второе"), list.stream().map(BoardChatService.ChatMessage::body).toList(), "сначала старые");
        assertTrue(list.get(0).mine());
        assertFalse(list.get(1).mine());
        assertThrows(AccessDeniedException.class, () -> chat.boardMessages(id, head));
        assertThrows(AccessDeniedException.class, () -> chat.post(id, null, "я чужой", head));
        assertThrows(FormValidationException.class, () -> chat.post(id, null, "   ", sales));
        chat.post(id, null, "x".repeat(2500), sales);
        var last = chat.boardMessages(id, sales).get(2);
        assertEquals(BoardChatService.MAX_LENGTH, last.body().length(), "длинный текст обрезается");
    }

    @Test
    void deleteByAuthorOrOwnerOnly() {
        Long id = boardWithRnd();
        chat.post(id, null, "от участника", rnd);
        chat.post(id, null, "от владельца", sales);
        var forRnd = chat.boardMessages(id, rnd);
        assertTrue(forRnd.get(0).canDelete(), "свое можно удалить");
        assertFalse(forRnd.get(1).canDelete(), "чужое участнику нельзя");
        assertTrue(chat.boardMessages(id, sales).get(0).canDelete(), "владелец удаляет любое");
        assertThrows(AccessDeniedException.class, () -> chat.delete(forRnd.get(1).id(), rnd));
        assertThrows(AccessDeniedException.class, () -> chat.delete(forRnd.get(0).id(), head));
        chat.delete(forRnd.get(0).id(), sales);
        assertEquals(1, chat.boardMessages(id, sales).size());
    }

    // ---------------------------------------------------------- чат карточки

    @Test
    void cardChatWorksOnlyOnOwnBoardsAndKeepsMessagesApart() {
        Long id = boardWithRnd();
        Task a = card(id, sales, sales.getId());
        Task b = card(id, sales, rnd.getId());
        chat.post(null, a.getId(), "про первую", rnd);
        chat.post(null, b.getId(), "про вторую", sales);
        chat.post(id, null, "общий", sales);
        assertEquals(List.of("про первую"), chat.cardMessages(a.getId(), sales).stream().map(BoardChatService.ChatMessage::body).toList());
        assertEquals(List.of("про вторую"), chat.cardMessages(b.getId(), rnd).stream().map(BoardChatService.ChatMessage::body).toList());
        assertEquals(List.of("общий"), chat.boardMessages(id, rnd).stream().map(BoardChatService.ChatMessage::body).toList(), "чаты не смешиваются");
        assertThrows(AccessDeniedException.class, () -> chat.cardMessages(a.getId(), head));
        assertThrows(AccessDeniedException.class, () -> chat.post(null, a.getId(), "посторонний", head));

        TaskForm inbox = new TaskForm();
        inbox.setTitle("Входящая " + marker());
        inbox.setAssigneeId(product.getId());
        Task incoming = tasks.createManual(inbox, product);
        assertThrows(AccessDeniedException.class, () -> chat.cardMessages(incoming.getId(), product), "на входящих запросах чата нет");
        assertThrows(AccessDeniedException.class, () -> chat.post(null, incoming.getId(), "x", product));
        assertThrows(AccessDeniedException.class, () -> activity.forTask(incoming.getId(), product));
    }

    // ---------------------------------------------------------- журнал

    @Test
    void logRecordsBoardAndCardEvents() {
        Long id = boardWithRnd();
        Task t = card(id, sales, rnd.getId());
        tasks.move(t.getId(), TaskStatus.IN_PROGRESS, rnd);
        tasks.reassign(t.getId(), sales.getId(), sales);
        tasks.move(t.getId(), TaskStatus.DONE, sales);
        tasks.archive(t.getId(), sales);
        tasks.unarchive(t.getId(), sales);
        Map<TaskStatus, String> titles = new EnumMap<>(TaskStatus.class);
        Map<TaskStatus, String> colors = new EnumMap<>(TaskStatus.class);
        titles.put(TaskStatus.NEW, "Идеи");
        titles.put(TaskStatus.IN_PROGRESS, "В работе");
        titles.put(TaskStatus.REVIEW, "На проверке");
        titles.put(TaskStatus.DONE, "Закрыто");
        colors.put(TaskStatus.NEW, "violet");
        colors.put(TaskStatus.IN_PROGRESS, "cyan");
        colors.put(TaskStatus.REVIEW, "amber");
        colors.put(TaskStatus.DONE, "green");
        boards.update(id, "Новое имя " + marker(), null, titles, colors, sales);
        boards.removeMember(id, rnd.getId(), sales);

        List<String> kinds = kinds(id);
        for (String k : List.of(BoardActivity.BOARD_CREATED, BoardActivity.MEMBER_ADDED, BoardActivity.CARD_CREATED,
                BoardActivity.CARD_MOVED, BoardActivity.CARD_ASSIGNED, BoardActivity.CARD_ARCHIVED, BoardActivity.CARD_RESTORED,
                BoardActivity.BOARD_RENAMED, BoardActivity.COLUMNS_CHANGED, BoardActivity.MEMBER_REMOVED)) {
            assertTrue(kinds.contains(k), "в журнале есть " + k);
        }
        assertEquals(2, kinds.stream().filter(BoardActivity.CARD_MOVED::equals).count());
        var moved = activity.forTask(t.getId(), sales).stream().filter(e -> e.kind().equals(BoardActivity.CARD_MOVED)).toList();
        assertEquals("Новые", moved.get(moved.size() - 1).arg1(), "названия колонок на момент перемещения");
        assertEquals("В работе", moved.get(moved.size() - 1).arg2());
        assertEquals(BoardActivity.MEMBER_REMOVED, activity.forBoard(id, sales).get(0).kind(), "свежие события сверху");
        assertTrue(activity.forTask(t.getId(), sales).stream().allMatch(e -> t.getId().equals(e.taskId())), "в журнале карточки только ее события");
        assertThrows(AccessDeniedException.class, () -> activity.forBoard(id, head));
        assertThrows(AccessDeniedException.class, () -> activity.forBoard(id, rnd), "убранный участник журнал больше не видит");
    }

    @Test
    void inboxTasksLeaveNoTraceInAnyBoardLog() {
        Long id = boardWithRnd();
        int before = activity.forBoard(id, sales).size();
        TaskForm f = new TaskForm();
        f.setTitle("Входящая " + marker());
        f.setAssigneeId(product.getId());
        Task t = tasks.createManual(f, product);
        tasks.move(t.getId(), TaskStatus.DONE, product);
        assertEquals(before, activity.forBoard(id, sales).size());
    }

    // ---------------------------------------------------------- страницы

    @Test
    @WithUserDetails("sales")
    void pagesAndHtmxFragments() throws Exception {
        Long id = boardWithRnd();
        Task t = card(id, sales, sales.getId());
        mvc.perform(get("/boards/" + id + "/chat")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"text\"")))
                .andExpect(content().string(containsString("Чат")));
        String text = "Привет " + marker();
        mvc.perform(post("/boards/" + id + "/chat").with(csrf()).header("HX-Request", "true").param("text", text))
                .andExpect(status().isOk()).andExpect(content().string(containsString(text)))
                .andExpect(content().string(not(containsString("<html"))));
        mvc.perform(get("/boards/" + id + "/chat/messages")).andExpect(content().string(containsString(text)));
        mvc.perform(post("/tasks/" + t.getId() + "/chat").with(csrf()).header("HX-Request", "true").param("text", "в карточку"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("в карточку")));
        mvc.perform(get("/tasks/" + t.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Обсуждение")))
                .andExpect(content().string(containsString("История карточки")))
                .andExpect(content().string(containsString("Карточка создана")))
                .andExpect(content().string(not(containsString("Создана карточка"))));
        mvc.perform(get("/boards/" + id + "/log")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Доска создана")))
                .andExpect(content().string(containsString("Создана карточка")));
    }

    @Test
    @WithUserDetails("sales")
    void inboxTaskPageHasNoChat() throws Exception {
        // продажник входящие создавать не может: берем задачу продакта и проверяем отказ на чат
        TaskForm g = new TaskForm();
        g.setTitle("Входящая " + marker());
        g.setAssigneeId(product.getId());
        Task t = tasks.createManual(g, product);
        mvc.perform(post("/tasks/" + t.getId() + "/chat").with(csrf()).param("text", "x")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("head")
    void outsiderIsForbidden() throws Exception {
        Long id = boardWithRnd();
        mvc.perform(get("/boards/" + id + "/chat")).andExpect(status().isForbidden());
        mvc.perform(get("/boards/" + id + "/log")).andExpect(status().isForbidden());
        mvc.perform(get("/boards/" + id + "/chat/messages")).andExpect(status().isForbidden());
        mvc.perform(post("/boards/" + id + "/chat").with(csrf()).param("text", "x")).andExpect(status().isForbidden());
    }
}
