package ru.eltex.producthub.task;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import ru.eltex.producthub.task.domain.TaskPriority;
import ru.eltex.producthub.task.service.BoardActivity;
import ru.eltex.producthub.task.service.BoardService;
import ru.eltex.producthub.task.service.TaskForm;
import ru.eltex.producthub.task.service.TaskService;

/** Правка названия, описания и приоритета карточки; флажки приоритета на доске. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class CardEditTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    BoardService boards;
    @Autowired
    TaskService tasks;
    @Autowired
    BoardActivity activity;
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

    private Long board() {
        Long id = boards.create("Правка " + UUID.randomUUID().toString().substring(0, 6), null, sales);
        boards.addMember(id, rnd.getId(), sales);
        return id;
    }

    private Task card(Long boardId) {
        TaskForm f = new TaskForm();
        f.setTitle("Старое название");
        f.setDescription("Старое описание");
        f.setBoardId(boardId);
        f.setAssigneeId(sales.getId());
        return tasks.createManual(f, sales);
    }

    @Test
    void membersEditTitleAndDescriptionAndItIsLogged() {
        Long id = board();
        Task t = card(id);
        Task edited = tasks.editCard(t.getId(), "  Новое название ", "Новое описание", rnd);
        assertEquals("Новое название", edited.getTitle());
        assertEquals("Новое описание", edited.getDescription());
        var kinds = activity.forTask(t.getId(), sales).stream().map(BoardActivity.EventView::kind).toList();
        assertTrue(kinds.contains(BoardActivity.CARD_RENAMED));
        assertTrue(kinds.contains(BoardActivity.CARD_DESCRIBED));
        var renamed = activity.forTask(t.getId(), sales).stream().filter(e -> e.kind().equals(BoardActivity.CARD_RENAMED)).findFirst().orElseThrow();
        assertEquals("Старое название", renamed.arg1());
        assertEquals("Новое название", renamed.arg2());

        int before = activity.forTask(t.getId(), sales).size();
        tasks.editCard(t.getId(), "Новое название", "Новое описание", sales);
        assertEquals(before, activity.forTask(t.getId(), sales).size(), "без изменений в журнал ничего не пишется");
        assertNull(tasks.editCard(t.getId(), "Новое название", "   ", sales).getDescription(), "пустое описание очищается");
    }

    @Test
    void editRulesOutsidersEmptyTitleAndInbox() {
        Long id = board();
        Task t = card(id);
        assertThrows(AccessDeniedException.class, () -> tasks.editCard(t.getId(), "Взлом", null, head));
        assertTrue(assertThrows(FormValidationException.class, () -> tasks.editCard(t.getId(), "  ", "x", sales))
                .getErrors().containsKey("title"));
        TaskForm f = new TaskForm();
        f.setTitle("Входящая");
        f.setAssigneeId(product.getId());
        Task inbox = tasks.createManual(f, product);
        assertThrows(AccessDeniedException.class, () -> tasks.editCard(inbox.getId(), "Другое", null, product),
                "входящие задачи название и описание не правят");
    }

    @Test
    void priorityChangeIsLoggedAndOnlyForThoseWhoCanMove() {
        Long id = board();
        Task t = card(id);
        assertEquals(TaskPriority.HIGH, tasks.setPriority(t.getId(), TaskPriority.HIGH, rnd).getPriority());
        var e = activity.forTask(t.getId(), sales).stream().filter(x -> x.kind().equals(BoardActivity.CARD_PRIORITY)).findFirst().orElseThrow();
        assertEquals("NORMAL", e.arg1());
        assertEquals("HIGH", e.arg2());
        int before = activity.forTask(t.getId(), sales).size();
        tasks.setPriority(t.getId(), TaskPriority.HIGH, rnd);
        assertEquals(before, activity.forTask(t.getId(), sales).size());
        assertThrows(AccessDeniedException.class, () -> tasks.setPriority(t.getId(), TaskPriority.LOW, head));
    }

    @Test
    @WithUserDetails("sales")
    void pagesShowFlagsAndEditForm() throws Exception {
        Long id = board();
        Task t = card(id);
        tasks.setPriority(t.getId(), TaskPriority.HIGH, sales);
        mvc.perform(get("/boards/" + id)).andExpect(status().isOk())
                .andExpect(content().string(containsString("prio-flag prio-HIGH")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        org.hamcrest.Matchers.not(containsString("Высокий</span>"))));
        mvc.perform(get("/tasks/" + t.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"priority\"")))
                .andExpect(content().string(containsString("Править название и описание")));
        mvc.perform(post("/tasks/" + t.getId() + "/edit").with(csrf()).param("title", "Из формы").param("description", "Текст"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/tasks/" + t.getId() + "/priority").with(csrf()).param("priority", "LOW"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/tasks/" + t.getId())).andExpect(content().string(containsString("Из формы")))
                .andExpect(content().string(containsString("prio-flag prio-LOW")))
                .andExpect(content().string(containsString("Переименована")));
    }
}
