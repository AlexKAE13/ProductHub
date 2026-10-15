package ru.eltex.producthub.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.feedback.domain.CommentStatus;
import ru.eltex.producthub.feedback.service.CommentInput;
import ru.eltex.producthub.feedback.service.CommentView;
import ru.eltex.producthub.feedback.service.FeedbackService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;

/** Комментарии к интерфейсу: привязка к странице и элементу, права, ответы, статусы, файл выгрузки. */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
class FeedbackTest {

    @Autowired
    FeedbackService service;
    @Autowired
    HubUserDetailsService users;
    @Autowired
    JdbcTemplate jdbc;

    HubUserDetails product;
    HubUserDetails sales;
    HubUserDetails head;
    HubUserDetails partner;
    String page;

    @BeforeEach
    void setUp() {
        product = users.loadUserByUsername("product");
        sales = users.loadUserByUsername("sales");
        head = users.loadUserByUsername("head");
        partner = users.loadUserByUsername("partner");
        page = "/sku/TEST-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private CommentInput onElement(String body) {
        return new CommentInput(page, "?tab=1", "Карточка SKU", "/sku/{*model}", "sku/card", "main > section.block:nth-of-type(3) > h2",
                "h2", "Прошивки", "<h2>", "Прошивки", "Прошивки Текущая версия 2.4.1", "1440x900", "BUG", body, null);
    }

    private CommentInput wholePage(String body) {
        return new CommentInput(page, "", "Карточка SKU", "/sku/{*model}", "sku/card", null, null, null, null, null, null, "1280x800",
                "IDEA", body, null);
    }

    // ----------------------------------------------------------- создание и привязка

    @Test
    void commentKeepsPageAndElementContext() {
        CommentView c = service.create(onElement("Заголовок слишком мелкий"), sales);
        assertTrue(c.onElement());
        assertEquals("NEW", c.status());
        assertEquals("BUG", c.kind());
        assertEquals(sales.getFullName(), c.authorName());

        var onPage = service.forPage(page, sales);
        assertEquals(1, onPage.size());
        assertEquals("main > section.block:nth-of-type(3) > h2", onPage.get(0).selector());
        assertEquals("Прошивки", onPage.get(0).section());
        assertTrue(service.forPage("/sku/OTHER-PAGE", sales).isEmpty(), "комментарии другой страницы не видны");
    }

    @Test
    void wholePageCommentHasNoElement() {
        CommentView c = service.create(wholePage("Страница тяжело читается"), head);
        assertFalse(c.onElement());
    }

    @Test
    void validationRejectsEmptyAndTooLong() {
        assertEquals("feedback.error.empty", assertThrows(FormValidationException.class,
                () -> service.create(wholePage("   "), sales)).getErrors().get("body"));
        assertEquals("feedback.error.long", assertThrows(FormValidationException.class,
                () -> service.create(wholePage("я".repeat(4001)), sales)).getErrors().get("body"));
        assertTrue(assertThrows(FormValidationException.class, () -> service.create(new CommentInput("evil", null, null, null, null,
                null, null, null, null, null, null, null, "BUG", "текст", null), sales)).getErrors().containsKey("page"));
    }

    @Test
    void longContextIsTrimmedNotRejected() {
        CommentInput in = new CommentInput(page, null, "т".repeat(900), null, null, "s".repeat(3000), "div", "т".repeat(2000),
                "<div>".repeat(900), "с".repeat(900), "к".repeat(2000), "1x1", "BUG", "ок", null);
        service.create(in, sales);
        assertEquals(1, service.forPage(page, sales).size());
    }

    @Test
    void repliesAttachToRootAndCannotNest() {
        CommentView root = service.create(onElement("Корневой"), sales);
        CommentInput reply = new CommentInput(page, null, null, null, null, null, null, null, null, null, null, null, "IDEA", "Согласен",
                root.id());
        CommentView r = service.create(reply, product);
        assertEquals(root.id(), r.parentId());
        var list = service.forPage(page, sales);
        assertEquals(1, list.size(), "ответ не отдельный комментарий");
        assertEquals("Согласен", list.get(0).replies().get(0).body());

        CommentInput nested = new CommentInput(page, null, null, null, null, null, null, null, null, null, null, null, "IDEA", "Ответ на ответ",
                r.id());
        assertThrows(FormValidationException.class, () -> service.create(nested, sales));
    }

    // ----------------------------------------------------------- права

    @Test
    void partnerCannotCommentOrRead() {
        assertThrows(AccessDeniedException.class, () -> service.create(wholePage("привет"), partner));
        assertThrows(AccessDeniedException.class, () -> service.forPage(page, partner));
    }

    @Test
    void onlyProductAndAdminManageAndExport() {
        service.create(wholePage("для разбора"), sales);
        for (HubUserDetails u : new HubUserDetails[] {sales, head, partner}) {
            assertThrows(AccessDeniedException.class, () -> service.all(null, null, null, u));
            assertThrows(AccessDeniedException.class, () -> service.exportMarkdown(null, null, null, u));
        }
        Long id = service.forPage(page, sales).get(0).id();
        assertThrows(AccessDeniedException.class, () -> service.setStatus(id, CommentStatus.DONE, null, sales));
        assertTrue(service.canManage(product));
        assertFalse(service.canManage(head));
    }

    @Test
    void resolvedMarkIsQuickAndReversibleAndOnlyForManagers() {
        CommentView c = service.create(wholePage("решить"), sales);
        assertThrows(AccessDeniedException.class, () -> service.setResolved(c.id(), true, sales));
        service.setStatus(c.id(), CommentStatus.ACCEPTED, "берем в работу", product);
        service.setResolved(c.id(), true, product);
        CommentView done = service.forPage(page, product).get(0);
        assertEquals("DONE", done.status());
        assertEquals("берем в работу", done.resolutionNote(), "пометка разбора сохраняется");
        service.setResolved(c.id(), false, product);
        assertEquals("NEW", service.forPage(page, product).get(0).status());
    }

    @Test
    void authorOrManagerCanDeleteOthersCannot() {
        CommentView c = service.create(wholePage("моё"), sales);
        assertThrows(AccessDeniedException.class, () -> service.delete(c.id(), head));
        service.delete(c.id(), sales);
        assertTrue(service.forPage(page, sales).isEmpty(), "удаление мягкое, но из выдачи пропало");
        assertEquals(1, jdbc.queryForObject("select count(*) from feedback_comment where id = ? and deleted = true", Integer.class, c.id()));

        CommentView d = service.create(wholePage("чужое"), sales);
        service.delete(d.id(), product);
        assertTrue(service.forPage(page, sales).isEmpty());
    }

    // ----------------------------------------------------------- статусы и выгрузка

    @Test
    void statusAndNoteAreSaved() {
        CommentView c = service.create(onElement("Поправить"), sales);
        service.setStatus(c.id(), CommentStatus.ACCEPTED, "Сделаем в понедельник", product);
        CommentView after = service.forPage(page, sales).get(0);
        assertEquals("ACCEPTED", after.status());
        assertEquals("Сделаем в понедельник", after.resolutionNote());
    }

    @Test
    void exportExplainsWhereEachCommentBelongs() {
        CommentView c = service.create(onElement("Название блока не по-русски\nи кнопка не видна"), sales);
        CommentInput reply = new CommentInput(page, null, null, null, null, null, null, null, null, null, null, null, "IDEA", "Поддерживаю",
                c.id());
        service.create(reply, head);
        service.create(wholePage("Общее замечание"), product);

        String md = service.exportMarkdown(null, page, null, product);
        assertTrue(md.contains("# Product HUB - правки к интерфейсу"));
        assertTrue(md.contains("## Страница " + page + " (Карточка SKU)"), "страница и ее заголовок");
        assertTrue(md.contains("src/main/resources/templates/sku/card.html"), "шаблон, где править");
        assertTrue(md.contains("/sku/{*model}"), "маршрут контроллера");
        assertTrue(md.contains("main > section.block:nth-of-type(3) > h2"), "путь к элементу");
        assertTrue(md.contains("Блок страницы: \"Прошивки\""));
        assertTrue(md.contains("Текст элемента: \"Прошивки\""));
        assertTrue(md.contains("Рядом: \"Прошивки Текущая версия 2.4.1\""));
        assertTrue(md.contains("Окно браузера: 1440x900"));
        assertTrue(md.contains("> Название блока не по-русски\n> и кнопка не видна"), "многострочный текст сохранен как цитата");
        assertTrue(md.contains("Поддерживаю"), "обсуждение");
        assertTrue(md.contains("Относится ко всей странице"));
        assertTrue(md.contains(sales.getFullName()) && md.contains(head.getFullName()));
        assertTrue(md.contains("Ошибка") && md.contains("Предложение"));
        assertFalse(md.contains("—"), "длинное тире в файле не используем");
    }

    @Test
    void exportSkipsClosedByDefaultButCanIncludeThem() {
        CommentView open = service.create(wholePage("открытый " + page), sales);
        CommentView done = service.create(wholePage("готовый " + page), sales);
        service.setStatus(done.id(), CommentStatus.DONE, "исправлено", product);

        String byDefault = service.exportMarkdown(null, page, null, product);
        assertTrue(byDefault.contains("открытый " + page));
        assertFalse(byDefault.contains("готовый " + page), "закрытые не попадают в файл по умолчанию");

        String onlyDone = service.exportMarkdown("DONE", page, null, product);
        assertTrue(onlyDone.contains("готовый " + page) && onlyDone.contains("Решение продакта:** исправлено"));
        assertFalse(onlyDone.contains("открытый " + page));
        assertEquals(open.id() + 1, done.id());
    }

    @Test
    void emptyExportSaysSo() {
        String md = service.exportMarkdown(null, "/net-takoy-stranicy", null, product);
        assertTrue(md.contains("Комментариев нет."));
    }
}
