package ru.eltex.producthub.feedback.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ru.eltex.producthub.feedback.domain.CommentKind;
import ru.eltex.producthub.feedback.domain.CommentStatus;
import ru.eltex.producthub.feedback.domain.FeedbackComment;

/**
 * Собирает файл с правками в Markdown. Файл рассчитан на то, что его читает разработчик: для каждого комментария
 * указано, что это за страница, где на ней элемент, что в нем написано, в каком шаблоне искать и что просит автор.
 */
@Component
public class FeedbackExporter {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    String render(List<FeedbackComment> flat, String exportedBy, LocalDateTime now) {
        Map<Long, List<FeedbackComment>> replies = flat.stream().filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(FeedbackComment::getParentId, LinkedHashMap::new, Collectors.toList()));
        List<FeedbackComment> roots = flat.stream().filter(c -> c.getParentId() == null).toList();
        Map<String, List<FeedbackComment>> byPage = roots.stream()
                .collect(Collectors.groupingBy(FeedbackComment::getPagePath, LinkedHashMap::new, Collectors.toList()));

        StringBuilder md = new StringBuilder();
        md.append("# Product HUB - правки к интерфейсу\n\n");
        md.append("Выгружено: ").append(now.format(WHEN)).append(", выгрузил: ").append(exportedBy)
                .append(", комментариев: ").append(roots.size()).append(", страниц: ").append(byPage.size()).append("\n\n");
        md.append("## Как читать этот файл\n\n");
        md.append("Комментарии оставлены пользователями прямо на страницах приложения и сгруппированы по страницам. ")
                .append("У каждого указаны: тип (Ошибка, Предложение, Вопрос), статус, автор, текст комментария и привязка:\n\n");
        md.append("- **Страница** - адрес в приложении и его заголовок. Для карточки SKU в адресе стоит артикул.\n");
        md.append("- **Шаблон** - файл шаблона Thymeleaf в `src/main/resources/templates/`, который рисует страницу, и маршрут контроллера.\n");
        md.append("- **Блок страницы** - заголовок раздела, внутри которого находится элемент.\n");
        md.append("- **Элемент** - CSS-путь до элемента, его тип и текст на экране. По нему можно найти место в шаблоне.\n");
        md.append("- **Рядом** - текст вокруг элемента, чтобы понять контекст.\n");
        md.append("- **Фрагмент HTML** - как элемент выглядит в разметке.\n");
        md.append("- Если элемента нет, комментарий относится ко всей странице.\n\n");
        md.append("Статусы: Новый - еще не разобран, Принят - будем делать, Сделано - уже исправлено, Отклонен - не делаем.\n\n");
        md.append("---\n\n");

        if (roots.isEmpty()) {
            md.append("Комментариев нет.\n");
            return md.toString();
        }
        for (Map.Entry<String, List<FeedbackComment>> page : byPage.entrySet()) {
            FeedbackComment first = page.getValue().get(0);
            md.append("## Страница ").append(page.getKey());
            if (notBlank(first.getPageTitle())) {
                md.append(" (").append(clean(first.getPageTitle())).append(")");
            }
            md.append(" - комментариев: ").append(page.getValue().size()).append("\n\n");
            if (notBlank(first.getViewName())) {
                md.append("- Шаблон: `").append(template(first.getViewName())).append("`\n");
            }
            if (notBlank(first.getRoutePattern())) {
                md.append("- Маршрут: `").append(first.getRoutePattern()).append("`\n");
            }
            md.append("\n");
            for (FeedbackComment c : page.getValue()) {
                comment(md, c, replies.getOrDefault(c.getId(), List.of()));
            }
        }
        return md.toString();
    }

    private void comment(StringBuilder md, FeedbackComment c, List<FeedbackComment> replies) {
        md.append("### Комментарий №").append(c.getId()).append(" - ").append(kind(c.getKind())).append(", ")
                .append(status(c.getStatus())).append("\n\n");
        md.append("- Автор: ").append(c.getAuthorName()).append(", ").append(c.getCreatedAt().format(WHEN)).append("\n");
        md.append("- Страница: ").append(c.getPagePath()).append(notBlank(c.getPageQuery()) ? c.getPageQuery() : "").append("\n");
        if (notBlank(c.getSelector())) {
            if (notBlank(c.getSectionTitle())) {
                md.append("- Блок страницы: \"").append(clean(c.getSectionTitle())).append("\"\n");
            }
            md.append("- Элемент: `").append(c.getSelector().replace("`", "'")).append("`");
            if (notBlank(c.getElementTag())) {
                md.append(" (тип: ").append(c.getElementTag()).append(")");
            }
            md.append("\n");
            if (notBlank(c.getElementText())) {
                md.append("- Текст элемента: \"").append(clean(c.getElementText())).append("\"\n");
            }
            if (notBlank(c.getContextText())) {
                md.append("- Рядом: \"").append(clean(c.getContextText())).append("\"\n");
            }
            if (notBlank(c.getElementHtml())) {
                md.append("- Фрагмент HTML: `").append(clean(c.getElementHtml()).replace("`", "'")).append("`\n");
            }
        } else {
            md.append("- Относится ко всей странице\n");
        }
        if (notBlank(c.getViewport())) {
            md.append("- Окно браузера: ").append(c.getViewport()).append("\n");
        }
        md.append("\n**Что просит автор:**\n\n");
        quote(md, c.getBody());
        if (!replies.isEmpty()) {
            md.append("\n**Обсуждение:**\n\n");
            for (FeedbackComment r : replies) {
                md.append("- ").append(r.getAuthorName()).append(", ").append(r.getCreatedAt().format(WHEN)).append(": ")
                        .append(clean(r.getBody())).append("\n");
            }
        }
        if (notBlank(c.getResolutionNote())) {
            md.append("\n**Решение продакта:** ").append(clean(c.getResolutionNote())).append("\n");
        }
        md.append("\n---\n\n");
    }

    private static void quote(StringBuilder md, String text) {
        for (String line : text.strip().split("\\R")) {
            md.append("> ").append(line).append("\n");
        }
    }

    /** "sku/card" -> "templates/sku/card.html", "artifacts/index :: results" -> "templates/artifacts/index.html". */
    static String template(String view) {
        String name = view.contains("::") ? view.substring(0, view.indexOf("::")).strip() : view;
        return "src/main/resources/templates/" + name + ".html";
    }

    private static String kind(CommentKind kind) {
        return switch (kind) {
            case BUG -> "Ошибка";
            case IDEA -> "Предложение";
            case QUESTION -> "Вопрос";
        };
    }

    private static String status(CommentStatus status) {
        return switch (status) {
            case NEW -> "Новый";
            case ACCEPTED -> "Принят";
            case REJECTED -> "Отклонен";
            case DONE -> "Сделано";
        };
    }

    private static String clean(String s) {
        return s.replaceAll("\\s+", " ").replace("\"", "'").strip();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
