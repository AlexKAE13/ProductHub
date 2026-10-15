package ru.eltex.producthub.feedback.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.feedback.domain.CommentKind;
import ru.eltex.producthub.feedback.domain.CommentStatus;
import ru.eltex.producthub.feedback.domain.FeedbackComment;
import ru.eltex.producthub.feedback.repo.FeedbackRepository;
import ru.eltex.producthub.security.HubUserDetails;

/** Комментарии к интерфейсу: создание, ответы, разбор статусов и выгрузка файла с правками. */
@Service
public class FeedbackService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final int BODY_MAX = 4000;

    private final FeedbackRepository repository;
    private final FeedbackPermissions permissions;
    private final FeedbackExporter exporter;

    public FeedbackService(FeedbackRepository repository, FeedbackPermissions permissions, FeedbackExporter exporter) {
        this.repository = repository;
        this.permissions = permissions;
        this.exporter = exporter;
    }

    public boolean canManage(HubUserDetails user) {
        return permissions.canManage(user);
    }

    // ------------------------------------------------------------ запись

    @Transactional
    public CommentView create(CommentInput in, HubUserDetails user) {
        if (!permissions.canComment(user)) {
            throw new AccessDeniedException("Комментировать могут только сотрудники");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        String body = in.body() == null ? "" : in.body().strip();
        if (body.isEmpty()) {
            errors.put("body", "feedback.error.empty");
        } else if (body.length() > BODY_MAX) {
            errors.put("body", "feedback.error.long");
        }
        if (in.pagePath() == null || !in.pagePath().startsWith("/")) {
            errors.put("page", "feedback.error.page");
        }
        if (!errors.isEmpty()) {
            throw new FormValidationException(errors);
        }
        CommentKind kind = parseKind(in.kind());
        FeedbackComment c = new FeedbackComment(user.getUsername(), user.getFullName(), kind, body, cut(in.pagePath(), 500));
        if (in.parentId() != null) {
            FeedbackComment parent = repository.findById(in.parentId()).filter(p -> !p.isDeleted() && p.getParentId() == null)
                    .orElseThrow(() -> new FormValidationException(Map.of("parent", "feedback.error.parent")));
            c.setParentId(parent.getId());
            // ответ относится к той же странице, что и исходный комментарий
            c.setPageQuery(parent.getPageQuery());
            c.setPageTitle(parent.getPageTitle());
            c.setRoutePattern(parent.getRoutePattern());
            c.setViewName(parent.getViewName());
        } else {
            c.setPageQuery(cut(in.pageQuery(), 500));
            c.setPageTitle(cut(in.pageTitle(), 300));
            c.setRoutePattern(cut(in.route(), 200));
            c.setViewName(cut(in.view(), 200));
            c.setSelector(cut(in.selector(), 1000));
            c.setElementTag(cut(in.tag(), 40));
            c.setElementText(cut(in.text(), 500));
            c.setElementHtml(cut(in.html(), 1000));
            c.setSectionTitle(cut(in.section(), 300));
            c.setContextText(cut(in.context(), 500));
            c.setViewport(cut(in.viewport(), 40));
        }
        return view(repository.save(c), List.of());
    }

    @Transactional
    public void setStatus(Long id, CommentStatus status, String note, HubUserDetails user) {
        requireManage(user);
        FeedbackComment c = repository.findById(id).filter(x -> !x.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Комментарий не найден"));
        c.setStatus(status);
        c.setResolutionNote(note == null || note.isBlank() ? null : cut(note.strip(), 1000));
    }

    /** Быстрая отметка "решено": меняет только статус (Сделано или снова Новый), пометку разбора не трогает. */
    @Transactional
    public void setResolved(Long id, boolean resolved, HubUserDetails user) {
        requireManage(user);
        FeedbackComment c = repository.findById(id).filter(x -> !x.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Комментарий не найден"));
        c.setStatus(resolved ? CommentStatus.DONE : CommentStatus.NEW);
    }

    @Transactional
    public void delete(Long id, HubUserDetails user) {
        FeedbackComment c = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Комментарий не найден"));
        if (!permissions.canManage(user) && !c.getAuthorLogin().equals(user.getUsername())) {
            throw new AccessDeniedException("Удалить комментарий может автор или продакт");
        }
        c.setDeleted(true);
    }

    // ------------------------------------------------------------ чтение

    /** Комментарии одной страницы (без параметров адреса): корневые с ответами. */
    @Transactional(readOnly = true)
    public List<CommentView> forPage(String path, HubUserDetails user) {
        if (!permissions.canComment(user)) {
            throw new AccessDeniedException("Нет доступа к комментариям");
        }
        return tree(repository.findByPagePathAndDeletedFalseOrderByIdAsc(path));
    }

    /** Все комментарии для страницы разбора, с фильтрами. Пустой фильтр - все. */
    @Transactional(readOnly = true)
    public List<CommentView> all(String status, String page, String author, HubUserDetails user) {
        requireManage(user);
        return tree(filtered(status, page, author));
    }

    /** Файл с правками для передачи разработчику. По умолчанию без закрытых (сделано и отклонено). */
    @Transactional(readOnly = true)
    public String exportMarkdown(String status, String page, String author, HubUserDetails user) {
        requireManage(user);
        List<FeedbackComment> list = filtered(status, page, author);
        boolean open = status == null || status.isBlank();
        if (open) {
            Set<CommentStatus> closed = Set.of(CommentStatus.DONE, CommentStatus.REJECTED);
            list = list.stream().filter(c -> c.getParentId() != null || !closed.contains(c.getStatus())).toList();
        }
        return exporter.render(list, user.getFullName(), LocalDateTime.now());
    }

    public List<String> pages() {
        return repository.findByDeletedFalseOrderByPagePathAscIdAsc().stream().map(FeedbackComment::getPagePath).distinct().toList();
    }

    // ------------------------------------------------------------ внутреннее

    private List<FeedbackComment> filtered(String status, String page, String author) {
        CommentStatus wanted = status == null || status.isBlank() || "ALL".equals(status) ? null : CommentStatus.valueOf(status);
        List<FeedbackComment> all = repository.findByDeletedFalseOrderByPagePathAscIdAsc();
        Set<Long> keep = all.stream().filter(c -> c.getParentId() == null)
                .filter(c -> wanted == null || c.getStatus() == wanted)
                .filter(c -> page == null || page.isBlank() || c.getPagePath().equals(page))
                .filter(c -> author == null || author.isBlank() || c.getAuthorLogin().equals(author))
                .map(FeedbackComment::getId).collect(Collectors.toSet());
        return all.stream().filter(c -> keep.contains(c.getId()) || (c.getParentId() != null && keep.contains(c.getParentId()))).toList();
    }

    private List<CommentView> tree(List<FeedbackComment> flat) {
        Map<Long, List<FeedbackComment>> replies = flat.stream().filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(FeedbackComment::getParentId));
        List<CommentView> out = new ArrayList<>();
        for (FeedbackComment c : flat) {
            if (c.getParentId() == null) {
                out.add(view(c, replies.getOrDefault(c.getId(), List.of()).stream().map(r -> view(r, List.of())).toList()));
            }
        }
        return out;
    }

    static CommentView view(FeedbackComment c, List<CommentView> replies) {
        return new CommentView(c.getId(), c.getParentId(), c.getAuthorName(), c.getAuthorLogin(), c.getKind().name(),
                c.getStatus().name(), c.getBody(), c.getSelector(), c.getElementText(), c.getSectionTitle(),
                c.getCreatedAt().format(WHEN), c.getResolutionNote(), c.getSelector() != null && !c.getSelector().isBlank(), c.getPagePath(), c.getPageQuery(),
                c.getPageTitle(), replies);
    }

    private void requireManage(HubUserDetails user) {
        if (!permissions.canManage(user)) {
            throw new AccessDeniedException("Разбирать комментарии могут продакты и администратор");
        }
    }

    private static CommentKind parseKind(String kind) {
        try {
            return kind == null ? CommentKind.IDEA : CommentKind.valueOf(kind);
        } catch (IllegalArgumentException e) {
            return CommentKind.IDEA;
        }
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
