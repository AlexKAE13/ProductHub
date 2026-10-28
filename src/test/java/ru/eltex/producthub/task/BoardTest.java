package ru.eltex.producthub.task;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
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
import ru.eltex.producthub.task.service.Board;
import ru.eltex.producthub.task.service.BoardService;
import ru.eltex.producthub.task.service.TaskFilter;
import ru.eltex.producthub.task.service.TaskForm;
import ru.eltex.producthub.task.service.TaskRuleException;
import ru.eltex.producthub.task.service.TaskService;

/** Свои доски задач: права, колонки, участники, архив; и видимость входящих запросов. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class BoardTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    BoardService boards;
    @Autowired
    TaskService tasks;
    @Autowired
    HubUserDetailsService users;

    HubUserDetails sales;
    HubUserDetails product;
    HubUserDetails rnd;
    HubUserDetails head;
    HubUserDetails admin;
    HubUserDetails partner;

    @BeforeEach
    void setUp() {
        sales = users.loadUserByUsername("sales");
        product = users.loadUserByUsername("product");
        rnd = users.loadUserByUsername("rnd");
        head = users.loadUserByUsername("head");
        admin = users.loadUserByUsername("admin");
        partner = users.loadUserByUsername("partner");
    }

    private static String marker() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Long newBoard(HubUserDetails owner) {
        return boards.create("Доска " + marker(), "для теста", owner);
    }

    private TaskForm card(Long boardId, Long assigneeId) {
        TaskForm f = new TaskForm();
        f.setTitle("Карточка " + marker());
        f.setBoardId(boardId);
        f.setAssigneeId(assigneeId);
        return f;
    }

    private List<Task> all(Board b) {
        return b.columns().values().stream().flatMap(List::stream).toList();
    }

    // ---------------------------------------------------------- создание и колонки

    @Test
    void anyInternalUserCreatesBoardWithFourDefaultColumns() {
        Long id = newBoard(sales);
        var info = boards.info(id, sales);
        assertTrue(info.owner());
        assertEquals(4, info.titles().size());
        assertEquals("Новые", info.titles().get(TaskStatus.NEW));
        assertEquals("Закрыто", info.titles().get(TaskStatus.DONE));
        assertEquals("blue", info.colors().get(TaskStatus.NEW));
        assertEquals("green", info.colors().get(TaskStatus.DONE));
        assertThrows(AccessDeniedException.class, () -> boards.create("Чужая", null, partner), "партнер досок не создает");
        assertThrows(FormValidationException.class, () -> boards.create("  ", null, sales));
    }

    @Test
    void ownerRenamesAndRecolorsColumnsOthersCannot() {
        Long id = newBoard(sales);
        boards.addMember(id, rnd.getId(), sales);
        Map<TaskStatus, String> titles = new EnumMap<>(TaskStatus.class);
        Map<TaskStatus, String> colors = new EnumMap<>(TaskStatus.class);
        titles.put(TaskStatus.NEW, "Идеи");
        titles.put(TaskStatus.IN_PROGRESS, "Делаем");
        titles.put(TaskStatus.REVIEW, "Смотрим");
        titles.put(TaskStatus.DONE, "Готово");
        colors.put(TaskStatus.NEW, "violet");
        colors.put(TaskStatus.IN_PROGRESS, "red");
        colors.put(TaskStatus.REVIEW, "не-цвет");
        colors.put(TaskStatus.DONE, "grey");
        assertThrows(AccessDeniedException.class, () -> boards.update(id, "Новое имя", null, titles, colors, rnd), "участник не правит");
        boards.update(id, "Новое имя", "описание", titles, colors, sales);
        var info = boards.info(id, rnd);
        assertEquals("Новое имя", info.name());
        assertEquals("Идеи", info.titles().get(TaskStatus.NEW));
        assertEquals("violet", info.colors().get(TaskStatus.NEW));
        assertEquals("amber", info.colors().get(TaskStatus.REVIEW), "неизвестный цвет заменяется стандартным");
        titles.put(TaskStatus.DONE, " ");
        assertThrows(FormValidationException.class, () -> boards.update(id, "x", null, titles, colors, sales));
    }

    // ---------------------------------------------------------- участники и доступ

    @Test
    void boardIsClosedToOutsidersUntilInvited() {
        Long id = newBoard(sales);
        assertThrows(AccessDeniedException.class, () -> boards.info(id, rnd));
        assertThrows(AccessDeniedException.class, () -> boards.view(id, new TaskFilter(null, null, null, null), false, rnd));
        assertThrows(AccessDeniedException.class, () -> boards.addMember(id, rnd.getId(), rnd), "приглашает только владелец");
        boards.addMember(id, rnd.getId(), sales);
        assertEquals(2, boards.info(id, rnd).members().size());
        assertFalse(boards.info(id, rnd).owner());
        assertThrows(AccessDeniedException.class, () -> boards.addMember(id, rnd.getId(), rnd), "участник приглашать не может");
        assertThrows(AccessDeniedException.class, () -> boards.addMember(id, partner.getId(), sales), "партнера не приглашают");
        assertTrue(boards.mine(rnd).stream().anyMatch(b -> b.id().equals(id)));
        assertTrue(boards.mine(head).stream().noneMatch(b -> b.id().equals(id)), "чужая доска в списке не видна");
    }

    @Test
    void cardsAreCreatedAndMovedByMembersOnlyAndAssigneeMustBeMember() {
        Long id = newBoard(sales);
        boards.addMember(id, rnd.getId(), sales);
        Task t = tasks.createManual(card(id, rnd.getId()), sales);
        assertEquals("rnd", t.getAssignee().getLogin());
        assertThrows(AccessDeniedException.class, () -> tasks.createManual(card(id, sales.getId()), head), "посторонний не создает");
        var bad = assertThrows(FormValidationException.class, () -> tasks.createManual(card(id, head.getId()), sales));
        assertTrue(bad.getErrors().containsKey("assigneeId"), "исполнитель только из участников");

        assertEquals(TaskStatus.IN_PROGRESS, tasks.move(t.getId(), TaskStatus.IN_PROGRESS, rnd).getStatus(), "участник двигает чужую карточку");
        assertThrows(AccessDeniedException.class, () -> tasks.move(t.getId(), TaskStatus.DONE, head));
        assertThrows(AccessDeniedException.class, () -> tasks.detail(t.getId(), head));
        assertEquals(t.getId(), tasks.detail(t.getId(), rnd).getId());
        Board view = boards.view(id, new TaskFilter(null, null, null, null), false, rnd);
        assertEquals(1, view.columns().get(TaskStatus.IN_PROGRESS).size());
        // карточки своей доски не попадают на входящие запросы
        assertTrue(all(tasks.board(new TaskFilter("all", null, null, null), admin)).stream().noneMatch(x -> x.getId().equals(t.getId())));
    }

    @Test
    void removedMemberCardsGoToOwner() {
        Long id = newBoard(sales);
        boards.addMember(id, rnd.getId(), sales);
        Task t = tasks.createManual(card(id, rnd.getId()), sales);
        assertThrows(AccessDeniedException.class, () -> boards.removeMember(id, sales.getId(), sales), "владельца убрать нельзя");
        boards.removeMember(id, rnd.getId(), sales);
        assertEquals("sales", tasks.detail(t.getId(), sales).getAssignee().getLogin());
        assertThrows(AccessDeniedException.class, () -> boards.info(id, rnd));
    }

    @Test
    void deletedBoardDisappearsForEveryone() {
        Long id = newBoard(sales);
        assertThrows(AccessDeniedException.class, () -> boards.delete(id, rnd));
        boards.delete(id, sales);
        assertTrue(boards.mine(sales).stream().noneMatch(b -> b.id().equals(id)));
        assertThrows(RuntimeException.class, () -> boards.info(id, sales));
    }

    // ---------------------------------------------------------- архив

    @Test
    void onlyClosedCardsGoToArchiveAndCanComeBack() {
        Long id = newBoard(sales);
        Task t = tasks.createManual(card(id, sales.getId()), sales);
        var e = assertThrows(TaskRuleException.class, () -> tasks.archive(t.getId(), sales));
        assertEquals("tasks.rule.archiveDone", e.getMessageKey());
        tasks.move(t.getId(), TaskStatus.DONE, sales);
        tasks.archive(t.getId(), sales);
        var filter = new TaskFilter(null, null, null, null);
        assertTrue(all(boards.view(id, filter, false, sales)).isEmpty(), "с доски ушла");
        assertEquals(1, all(boards.view(id, filter, true, sales)).size(), "лежит в архиве");
        assertThrows(AccessDeniedException.class, () -> tasks.archive(t.getId(), head), "чужой доски не касается");
        tasks.unarchive(t.getId(), sales);
        assertEquals(1, boards.view(id, filter, false, sales).columns().get(TaskStatus.DONE).size(), "вернулась в закрытые");
        assertTrue(all(boards.view(id, filter, true, sales)).isEmpty());
    }

    // ---------------------------------------------------------- входящие запросы

    @Test
    void inboxIsVisibleToAssigneeAndHeadOnly() {
        Task t = tasks.createManual(card(null, product.getId()), product);
        var all = new TaskFilter("all", null, null, null);
        assertTrue(all(tasks.board(all, product)).stream().anyMatch(x -> x.getId().equals(t.getId())), "получатель видит свое");
        assertTrue(all(tasks.board(all, head)).stream().anyMatch(x -> x.getId().equals(t.getId())), "руководитель видит все");
        assertTrue(all(tasks.board(all, admin)).stream().anyMatch(x -> x.getId().equals(t.getId())));
        HubUserDetails other = users.loadUserByUsername("kuznetsov");
        assertTrue(all(tasks.board(all, other)).stream().noneMatch(x -> x.getId().equals(t.getId())),
                "другой продакт чужие запросы не видит, даже с фильтром Все");
        assertThrows(AccessDeniedException.class, () -> tasks.detail(t.getId(), other));
        assertThrows(AccessDeniedException.class, () -> tasks.move(t.getId(), TaskStatus.IN_PROGRESS, other));
    }

    @Test
    void inboxArchiveWorksForClosedTasks() {
        Task t = tasks.createManual(card(null, product.getId()), product);
        assertThrows(TaskRuleException.class, () -> tasks.archive(t.getId(), product));
        tasks.move(t.getId(), TaskStatus.DONE, product);
        tasks.archive(t.getId(), product);
        var filter = new TaskFilter("me", null, null, null);
        assertTrue(all(tasks.board(filter, product, false)).stream().noneMatch(x -> x.getId().equals(t.getId())));
        assertTrue(all(tasks.board(filter, product, true)).stream().anyMatch(x -> x.getId().equals(t.getId())));
    }

    // ---------------------------------------------------------- страницы

    @Test
    @WithUserDetails("sales")
    void salesGetsBoardsPageInsteadOfInboxAndCreatesBoardThroughForm() throws Exception {
        mvc.perform(get("/boards")).andExpect(status().isOk()).andExpect(content().string(containsString("Новая доска")));
        String name = "Веб-доска " + marker();
        var result = mvc.perform(post("/boards").with(csrf()).param("name", name).param("description", "d"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/boards/*")).andReturn();
        String url = result.getResponse().getRedirectedUrl();
        mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(content().string(containsString(name)))
                .andExpect(content().string(containsString("Новые")))
                .andExpect(content().string(containsString("Закрыто")))
                .andExpect(content().string(containsString("Настройки")));
        mvc.perform(get(url + "/settings")).andExpect(status().isOk())
                .andExpect(content().string(containsString("title.NEW")))
                .andExpect(content().string(containsString("color.DONE")));
        mvc.perform(get(url + "/archive")).andExpect(status().isOk());
        mvc.perform(get(url + "/cards/new")).andExpect(status().isOk()).andExpect(content().string(containsString("name=\"boardId\"")));
    }

    @Test
    @WithUserDetails("rnd")
    void outsiderGetsForbiddenOnForeignBoardPages() throws Exception {
        Long id = newBoard(sales);
        mvc.perform(get("/boards/" + id)).andExpect(status().isForbidden());
        mvc.perform(get("/boards/" + id + "/settings")).andExpect(status().isForbidden());
        mvc.perform(post("/boards/" + id + "/delete").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("partner")
    void partnerHasNoBoards() throws Exception {
        mvc.perform(post("/boards").with(csrf()).param("name", "x")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("sales")
    void archiveEndpointsAnswerWithConflictForOpenCards() throws Exception {
        Long id = newBoard(sales);
        Task t = tasks.createManual(card(id, sales.getId()), sales);
        mvc.perform(post("/tasks/" + t.getId() + "/archive").with(csrf())).andExpect(status().isConflict())
                .andExpect(content().string(containsString("только закрытую задачу")));
        mvc.perform(post("/tasks/" + t.getId() + "/status").with(csrf()).param("status", "DONE")).andExpect(status().isNoContent());
        mvc.perform(post("/tasks/" + t.getId() + "/archive").with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/boards/" + id + "/archive")).andExpect(content().string(containsString(t.getTitle())));
        mvc.perform(post("/tasks/" + t.getId() + "/unarchive").with(csrf())).andExpect(status().isNoContent());
    }
}
