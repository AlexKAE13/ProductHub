package ru.eltex.producthub.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.importer.SeedImportService;
import ru.eltex.producthub.qa.domain.Question;
import ru.eltex.producthub.qa.domain.QuestionKind;
import ru.eltex.producthub.qa.domain.QuestionStatus;
import ru.eltex.producthub.qa.domain.TargetType;
import ru.eltex.producthub.qa.event.EditProposed;
import ru.eltex.producthub.qa.event.QuestionCreated;
import ru.eltex.producthub.qa.event.QuestionResolved;
import ru.eltex.producthub.qa.service.QuestionService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;
import ru.eltex.producthub.sku.service.SkuService;

@SpringBootTest
@ActiveProfiles({"dev", "test"})
@RecordApplicationEvents
class QuestionFlowTest {

    @Autowired
    QuestionService service;
    @Autowired
    SkuService skuService;
    @Autowired
    HubUserDetailsService users;
    @Autowired
    SeedImportService importer;
    @Autowired
    ApplicationEvents events;
    @Autowired
    ApplicationEventPublisher publisher;

    HubUserDetails product;
    HubUserDetails sales;
    HubUserDetails serviceCenter;
    HubUserDetails rnd;
    HubUserDetails partner;
    Long skuId;

    @BeforeEach
    void setUp() {
        importer.execute("test");
        product = users.loadUserByUsername("product");
        sales = users.loadUserByUsername("sales");
        serviceCenter = users.loadUserByUsername("service");
        rnd = users.loadUserByUsername("rnd");
        partner = users.loadUserByUsername("partner");
        skuId = skuService.findIdByModel("WEP-550K");
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void everyInternalRoleCanAskQuestionAndPartnerCannot() {
        for (String login : List.of("product", "service", "sales", "rnd", "head", "admin")) {
            Question q = service.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Вопрос от " + login + " " + unique(),
                    false, users.loadUserByUsername(login));
            assertEquals(QuestionStatus.NEW, q.getStatus());
        }
        assertThrows(AccessDeniedException.class,
                () -> service.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Можно?", false, partner));
    }

    @Test
    void questionAndEditPublishDifferentEvents() {
        long q0 = events.stream(QuestionCreated.class).count();
        long e0 = events.stream(EditProposed.class).count();
        service.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Вопрос " + unique(), false, sales);
        service.ask(QuestionKind.EDIT_PROPOSAL, TargetType.SKU, skuId, "Правка " + unique(), false, sales);
        assertEquals(q0 + 1, events.stream(QuestionCreated.class).count());
        assertEquals(e0 + 1, events.stream(EditProposed.class).count());
    }

    @Test
    void emptyAndTooLongTextIsRejected() {
        assertTrue(assertThrows(FormValidationException.class,
                () -> service.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "   ", false, sales)).getErrors().containsKey("text"));
        assertTrue(assertThrows(FormValidationException.class,
                () -> service.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "я".repeat(4001), false, sales)).getErrors().containsKey("text"));
    }

    @Test
    void questionAboutUnknownObjectIsRejected() {
        assertThrows(RuntimeException.class,
                () -> service.ask(QuestionKind.QUESTION, TargetType.SKU, 99999999L, "Что это?", false, sales));
    }

    // ---------------------------------------------------------- ответы и права

    @Test
    void productAnswersAndFaqAppearsInBlock() {
        String text = "Какая память у точки доступа " + unique() + "?";
        Question q = service.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, text, false, sales);
        long r0 = events.stream(QuestionResolved.class).count();
        service.answer(q.getId(), "Два гигабайта", true, product);
        assertEquals(r0 + 1, events.stream(QuestionResolved.class).count());

        var view = service.view(TargetType.SKU, skuId, sales);
        assertTrue(view.faq().stream().anyMatch(f -> f.getText().equals(text) && f.getAnswer().equals("Два гигабайта")));
        assertTrue(view.open().stream().noneMatch(o -> o.getId().equals(q.getId())), "отвеченный вопрос больше не открыт");
    }

    @Test
    void answerNotPublishedToFaqStaysOutOfBlock() {
        String text = "Закрытый вопрос " + unique();
        Question q = service.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, text, false, sales);
        service.answer(q.getId(), "Только автору", false, product);
        assertTrue(service.view(TargetType.SKU, skuId, sales).faq().stream().noneMatch(f -> f.getText().equals(text)));
        assertTrue(service.myQuestions(sales).stream().anyMatch(m -> m.getId().equals(q.getId()) && "Только автору".equals(m.getAnswer())),
                "автор видит ответ в Мои вопросы");
    }

    @Test
    void salesCannotAnswerAndServiceCenterAnswersOnlyTechnical() {
        Question plain = service.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Обычный " + unique(), false, sales);
        Question tech = service.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Технический " + unique(), true, sales);
        assertThrows(AccessDeniedException.class, () -> service.answer(plain.getId(), "ответ", true, sales));
        assertThrows(AccessDeniedException.class, () -> service.answer(plain.getId(), "ответ", true, rnd));
        assertThrows(AccessDeniedException.class, () -> service.answer(plain.getId(), "ответ", true, serviceCenter));
        service.answer(tech.getId(), "Отвечает сервисный центр", true, serviceCenter);
        assertEquals(QuestionStatus.ANSWERED, service.get(tech.getId()).getStatus());
    }

    @Test
    void answerableFlagFollowsRoles() {
        Question plain = service.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Для проверки прав " + unique(), false, sales);
        Question tech = service.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Техн для проверки прав " + unique(), true, sales);
        assertTrue(service.view(TargetType.SKU, skuId, product).answerable().contains(plain.getId()));
        assertFalse(service.view(TargetType.SKU, skuId, sales).answerable().contains(plain.getId()));
        var forService = service.view(TargetType.SKU, skuId, serviceCenter).answerable();
        assertTrue(forService.contains(tech.getId()));
        assertFalse(forService.contains(plain.getId()));
    }

    @Test
    void editProposalIsAcceptedOrRejectedWithoutChangingTheCard() {
        Question a = service.ask(QuestionKind.EDIT_PROPOSAL, TargetType.SKU, skuId, "Поправьте мощность " + unique(), false, sales);
        Question b = service.ask(QuestionKind.EDIT_PROPOSAL, TargetType.SKU, skuId, "Лишнее " + unique(), false, sales);
        String nameBefore = skuService.loadCard("WEP-550K", product).sku().getNameRu();
        service.resolveEdit(a.getId(), true, "Принято, исправлю", product);
        service.resolveEdit(b.getId(), false, "Не подходит", product);
        assertEquals(QuestionStatus.ACCEPTED, service.get(a.getId()).getStatus());
        assertEquals(QuestionStatus.REJECTED, service.get(b.getId()).getStatus());
        assertEquals("Принято, исправлю", service.get(a.getId()).getAnswer());
        assertEquals(nameBefore, skuService.loadCard("WEP-550K", product).sku().getNameRu());
        assertThrows(AccessDeniedException.class, () -> service.resolveEdit(
                service.ask(QuestionKind.EDIT_PROPOSAL, TargetType.SKU, skuId, "Еще " + unique(), false, sales).getId(),
                true, null, sales));
    }

    @Test
    void cannotAnswerTwiceOrAnswerEditAsQuestion() {
        Question q = service.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Дважды " + unique(), false, sales);
        service.answer(q.getId(), "Раз", true, product);
        assertThrows(FormValidationException.class, () -> service.answer(q.getId(), "Два", true, product));
        Question edit = service.ask(QuestionKind.EDIT_PROPOSAL, TargetType.SKU, skuId, "Правка " + unique(), false, sales);
        assertThrows(FormValidationException.class, () -> service.answer(edit.getId(), "ответ", true, product));
        assertThrows(FormValidationException.class, () -> service.answer(
                service.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Пустой " + unique(), false, sales).getId(), "  ", true, product));
    }

    @Test
    void authorOrProductClosesQuestion() {
        Question q = service.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Закрыть " + unique(), false, sales);
        assertThrows(AccessDeniedException.class, () -> service.close(q.getId(), rnd));
        service.close(q.getId(), sales);
        assertEquals(QuestionStatus.CLOSED, service.get(q.getId()).getStatus());
    }

    // ---------------------------------------------------------- "Возможно, ответ уже есть"

    @Test
    void similarAnsweredQuestionIsSuggested() {
        String marker = unique();
        Question q = service.ask(QuestionKind.QUESTION, TargetType.SKU, skuId,
                "Какая последняя прошивка поддерживает WPA3 " + marker + " на WEP-550K?", false, sales);
        service.answer(q.getId(), "Начиная с версии 2.4", true, product);

        var found = service.similar("последняя прошивка WPA3 " + marker, TargetType.SKU, skuId, rnd);
        assertFalse(found.isEmpty());
        assertEquals(q.getId(), found.get(0).question().getId());
        assertEquals("/sku/WEP-550K", found.get(0).targetUrl());

        // другие формы слов тоже находят (окончания обрезаются)
        assertFalse(service.similar("Подскажите прошивку WPA3 " + marker, TargetType.SKU, skuId, rnd).isEmpty());
        // посторонний вопрос ничего не находит
        assertTrue(service.similar("Сколько стоит доставка в Новосибирск", TargetType.SKU, skuId, rnd).isEmpty());
        assertTrue(service.similar("  ", TargetType.SKU, skuId, rnd).isEmpty());
        // открытые вопросы без ответа не предлагаются
        Question open = service.ask(QuestionKind.QUESTION, TargetType.SKU, skuId, "Уникальнейшая абракадабра " + marker + "zz вопрос", false, sales);
        assertTrue(service.similar("Уникальнейшая абракадабра " + marker + "zz вопрос", TargetType.SKU, skuId, rnd).stream()
                .noneMatch(s -> s.question().getId().equals(open.getId())));
    }

    @Test
    void partnerGetsNoQaBlock() {
        var view = service.view(TargetType.SKU, skuId, partner);
        assertFalse(view.canAsk());
        assertTrue(view.faq().isEmpty());
        assertThrows(AccessDeniedException.class, () -> service.similar("прошивка", TargetType.SKU, skuId, partner));
    }
}
