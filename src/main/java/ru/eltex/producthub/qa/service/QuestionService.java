package ru.eltex.producthub.qa.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.qa.domain.Question;
import ru.eltex.producthub.qa.domain.QuestionKind;
import ru.eltex.producthub.qa.domain.QuestionStatus;
import ru.eltex.producthub.qa.domain.TargetType;
import ru.eltex.producthub.qa.event.EditProposed;
import ru.eltex.producthub.qa.event.QuestionCreated;
import ru.eltex.producthub.qa.event.QuestionResolved;
import ru.eltex.producthub.qa.repo.QuestionRepository;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.Role;
import ru.eltex.producthub.security.UserRepository;
import ru.eltex.producthub.sku.service.SkuPermissions;

/**
 * Вопросы и правки (SPEC.md 5.8). Задать вопрос может любой внутренний пользователь. Отвечают и принимают правки
 * PRODUCT и ADMIN, сервисный центр только на технические. Принятая правка в карточке сама ничего не меняет:
 * владелец вносит изменение вручную.
 */
@Service
public class QuestionService {

    private static final int LIMIT = 100;
    private static final int SIMILAR_CANDIDATES = 400;
    private static final int SIMILAR_RESULTS = 5;
    private static final int PREFIX = 5;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}.\\-]{2,}");

    private final QuestionRepository repository;
    private final QuestionTargets targets;
    private final SkuPermissions skuPermissions;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    public QuestionService(QuestionRepository repository, QuestionTargets targets, SkuPermissions skuPermissions,
                           UserRepository users, ApplicationEventPublisher events) {
        this.repository = repository;
        this.targets = targets;
        this.skuPermissions = skuPermissions;
        this.users = users;
        this.events = events;
    }

    // ------------------------------------------------------------------ права

    public boolean canAsk(HubUserDetails user) {
        return skuPermissions.isInternal(user);
    }

    public boolean canAnswer(HubUserDetails user, Question q) {
        if (user.getRoles().contains(Role.PRODUCT) || user.getRoles().contains(Role.ADMIN)) {
            return true;
        }
        return user.getRoles().contains(Role.SERVICE_CENTER) && q.isTechnical();
    }

    // ------------------------------------------------------------------ вопросы

    @Transactional
    public Question ask(QuestionKind kind, TargetType type, Long targetId, String text, boolean technical,
                        HubUserDetails user) {
        if (!canAsk(user)) {
            throw new AccessDeniedException("Вопросы доступны внутренним пользователям");
        }
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            throw new FormValidationException(Map.of("text", "validation.required"));
        }
        if (t.length() > 4000) {
            throw new FormValidationException(Map.of("text", "validation.qa.tooLong"));
        }
        targets.resolve(type, targetId).orElseThrow(() -> new QuestionNotFoundException(targetId));
        var author = users.findById(user.getId()).orElseThrow();
        Question q = repository.save(new Question(kind, type, targetId, author, t, technical));
        events.publishEvent(kind == QuestionKind.QUESTION ? new QuestionCreated(q.getId()) : new EditProposed(q.getId()));
        return q;
    }

    @Transactional
    public void answer(Long id, String answer, boolean publishToFaq, HubUserDetails user) {
        Question q = load(id);
        if (q.getKind() != QuestionKind.QUESTION || !q.getStatus().isOpen()) {
            throw new FormValidationException(Map.of("answer", "validation.qa.notOpen"));
        }
        requireAnswer(user, q);
        String a = answer == null ? "" : answer.trim();
        if (a.isEmpty()) {
            throw new FormValidationException(Map.of("answer", "validation.required"));
        }
        q.resolve(QuestionStatus.ANSWERED, a, publishToFaq, users.findById(user.getId()).orElseThrow());
        events.publishEvent(new QuestionResolved(id));
    }

    /** Принять или отклонить предложенную правку (комментарий необязателен). */
    @Transactional
    public void resolveEdit(Long id, boolean accept, String comment, HubUserDetails user) {
        Question q = load(id);
        if (q.getKind() != QuestionKind.EDIT_PROPOSAL || !q.getStatus().isOpen()) {
            throw new FormValidationException(Map.of("answer", "validation.qa.notOpen"));
        }
        requireAnswer(user, q);
        String c = comment == null || comment.isBlank() ? null : comment.trim();
        q.resolve(accept ? QuestionStatus.ACCEPTED : QuestionStatus.REJECTED, c, false,
                users.findById(user.getId()).orElseThrow());
        events.publishEvent(new QuestionResolved(id));
    }

    /** Закрыть вопрос: автор (свой), PRODUCT или ADMIN. */
    @Transactional
    public void close(Long id, HubUserDetails user) {
        Question q = load(id);
        boolean own = q.getAuthor().getId().equals(user.getId());
        if (!own && !user.getRoles().contains(Role.PRODUCT) && !user.getRoles().contains(Role.ADMIN)) {
            throw new AccessDeniedException("Закрыть вопрос может автор или продакт");
        }
        boolean wasOpen = q.getStatus().isOpen();
        q.setStatus(QuestionStatus.CLOSED);
        if (wasOpen) {
            events.publishEvent(new QuestionResolved(id));
        }
    }

    /** Берет вопрос в работу (задача перетащена в "В работе"). */
    @Transactional
    public void markInProgress(Long id) {
        repository.findById(id).ifPresent(q -> {
            if (q.getStatus() == QuestionStatus.NEW) {
                q.setStatus(QuestionStatus.IN_PROGRESS);
            }
        });
    }

    /** Адрес страницы объекта, к которому относится вопрос. */
    @Transactional(readOnly = true)
    public String targetUrl(TargetType type, Long id) {
        return targets.resolve(type, id).map(QuestionTargets.Target::url).orElse("/questions/my");
    }

    @Transactional(readOnly = true)
    public Question get(Long id) {
        return load(id);
    }

    public boolean isResolved(Question q) {
        return !q.getStatus().isOpen();
    }

    // ------------------------------------------------------------------ чтение

    @Transactional(readOnly = true)
    public List<Question> myQuestions(HubUserDetails user) {
        return repository.findByAuthor(user.getId(), PageRequest.of(0, 200));
    }

    /** Блок 12 страницы объекта: FAQ, открытые вопросы и признак, на что пользователь может ответить. */
    @Transactional(readOnly = true)
    public QaView view(TargetType type, Long id, HubUserDetails user) {
        var target = targets.resolve(type, id).orElseThrow(() -> new QuestionNotFoundException(id));
        if (!canAsk(user)) {
            return new QaView(type, id, target.url(), List.of(), List.of(), Set.of(), false);
        }
        List<Question> all = repository.findByTarget(type, id, PageRequest.of(0, LIMIT));
        List<Question> faq = new ArrayList<>();
        List<Question> open = new ArrayList<>();
        Set<Long> answerable = new HashSet<>();
        for (Question q : all) {
            if (q.getStatus() == QuestionStatus.ANSWERED && q.isPublishToFaq()) {
                faq.add(q);
            } else if (q.getStatus().isOpen()) {
                open.add(q);
                if (canAnswer(user, q)) {
                    answerable.add(q.getId());
                }
            }
        }
        return new QaView(type, id, target.url(), faq, open, answerable, canAsk(user));
    }

    // ------------------------------------------------------------------ "Возможно, ответ уже есть"

    /**
     * Ищет похожие отвеченные вопросы по общим словам (с обрезкой окончаний до 5 символов). Вопросы про тот же
     * объект получают небольшую прибавку. Полноценный поиск (Lucene) появится на этапе 5.
     */
    @Transactional(readOnly = true)
    public List<SimilarQuestion> similar(String text, TargetType type, Long targetId, HubUserDetails user) {
        if (!canAsk(user)) {
            throw new AccessDeniedException("Нет доступа");
        }
        Set<String> wanted = stems(text);
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<SimilarQuestion> found = new ArrayList<>();
        for (Question q : repository.findAnswered(PageRequest.of(0, SIMILAR_CANDIDATES))) {
            Set<String> own = stems(q.getText());
            Set<String> common = new HashSet<>(wanted);
            common.retainAll(own);
            int minCommon = wanted.size() <= 2 ? 1 : 2;
            if (common.size() < minCommon) {
                continue;
            }
            double score = (double) common.size() / wanted.size();
            if (score < 0.4) {
                continue;
            }
            if (q.getTargetType() == type && q.getTargetId().equals(targetId)) {
                score += 0.2;
            }
            String url = targets.resolve(q.getTargetType(), q.getTargetId()).map(QuestionTargets.Target::url).orElse(null);
            found.add(new SimilarQuestion(q, score, url));
        }
        found.sort((a, b) -> Double.compare(b.score(), a.score()));
        return found.size() > SIMILAR_RESULTS ? found.subList(0, SIMILAR_RESULTS) : found;
    }

    static Set<String> stems(String text) {
        Set<String> result = new java.util.LinkedHashSet<>();
        if (text == null) {
            return result;
        }
        Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String w = m.group().replaceAll("[.\\-]+$", "");
            if (w.length() < 4 && !w.matches(".*\\d.*")) {
                continue;
            }
            result.add(w.length() > PREFIX ? w.substring(0, PREFIX) : w);
        }
        return result;
    }

    private void requireAnswer(HubUserDetails user, Question q) {
        if (!canAnswer(user, q)) {
            throw new AccessDeniedException("Отвечать на вопросы и принимать правки могут PRODUCT и ADMIN, "
                    + "сервисный центр только на технические");
        }
    }

    private Question load(Long id) {
        return repository.findDetailed(id).orElseThrow(() -> new QuestionNotFoundException(id));
    }

    @SuppressWarnings("unused")
    private static Map<String, String> none() {
        return new LinkedHashMap<>();
    }
}
