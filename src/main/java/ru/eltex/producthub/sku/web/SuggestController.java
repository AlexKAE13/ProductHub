package ru.eltex.producthub.sku.web;

import java.util.List;
import java.util.Locale;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import ru.eltex.producthub.sku.service.ModelNormalizer;
import ru.eltex.producthub.sku.service.ReferenceDataService;

/**
 * Подсказки артикулов и линеек в полях форм (загрузка материалов, прошивки, кейсы, задачи).
 * Доступны всем внутренним ролям (общее правило безопасности), внешним пользователям ничего не отдают.
 */
@Controller
public class SuggestController {

    private static final int LIMIT = 8;

    @PersistenceContext
    private EntityManager em;

    private final ReferenceDataService reference;

    public SuggestController(ReferenceDataService reference) {
        this.reference = reference;
    }

    public record Item(String value, String hint) {
    }

    /** Артикулы по началу или части номера и по наименованию. Снятые с производства показываются тоже: материалы к ним привязывают. */
    @GetMapping("/suggest/skus")
    @ResponseBody
    @Transactional(readOnly = true)
    public List<Item> skus(@RequestParam String q) {
        String text = q == null ? "" : q.strip();
        if (text.isEmpty() || text.length() > 60) {
            return List.of();
        }
        String norm = ModelNormalizer.normalize(text);
        String like = "%" + text.toLowerCase(Locale.ROOT) + "%";
        List<Object[]> rows = em.createQuery("select s.model, s.nameRu, l.code, l.family from Sku s join s.line l "
                        + "where s.deleted = false and (s.modelNormalized like :norm or lower(s.nameRu) like :like) "
                        + "order by case when s.modelNormalized like :start then 0 else 1 end, s.model", Object[].class)
                .setParameter("norm", "%" + norm + "%")
                .setParameter("start", norm + "%")
                .setParameter("like", like)
                .setMaxResults(LIMIT)
                .getResultList();
        return rows.stream().map(r -> new Item((String) r[0], hint((String) r[1], (String) r[2], (String) r[3]))).toList();
    }

    @GetMapping("/suggest/lines")
    @ResponseBody
    public List<Item> lines(@RequestParam String q) {
        String text = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        return reference.lines().stream()
                .filter(l -> text.isEmpty() || l.getCode().toLowerCase(Locale.ROOT).contains(text)
                        || (l.getName() != null && l.getName().toLowerCase(Locale.ROOT).contains(text)))
                .sorted((a, b) -> Boolean.compare(!a.getCode().toLowerCase(Locale.ROOT).startsWith(text),
                        !b.getCode().toLowerCase(Locale.ROOT).startsWith(text)))
                .limit(LIMIT)
                .map(l -> new Item(l.getCode(), hint(l.getName(), l.getFamily(), null)))
                .toList();
    }

    private static String hint(String name, String a, String b) {
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.isBlank()) {
            sb.append(name);
        }
        for (String part : new String[] {a, b}) {
            if (part != null && !part.isBlank()) {
                sb.append(sb.length() > 0 ? " · " : "").append(part);
            }
        }
        return sb.toString();
    }
}
