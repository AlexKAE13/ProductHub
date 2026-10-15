package ru.eltex.producthub.tag.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.common.AuditAction;
import ru.eltex.producthub.common.AuditService;
import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.Role;
import ru.eltex.producthub.sku.service.CacheConfig;
import ru.eltex.producthub.tag.domain.Tag;
import ru.eltex.producthub.tag.domain.TagCategory;
import ru.eltex.producthub.tag.repo.TagRepository;

/** Справочник тегов. Создавать теги могут только PRODUCT и ADMIN, произвольных меток нет (SPEC.md 4.7). */
@Service
public class TagService {

    public static final String ENTITY = "TAG";

    private final TagRepository tags;
    private final AuditService audit;

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public TagService(TagRepository tags, AuditService audit, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.tags = tags;
        this.audit = audit;
    }

    @Cacheable(CacheConfig.TAGS)
    @Transactional(readOnly = true)
    public List<Tag> all() {
        return tags.findAllByDeletedFalseOrderByCategoryAscValueAsc();
    }

    public List<Tag> active() {
        return all().stream().filter(Tag::isActive).toList();
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.TAGS, allEntries = true)
    public Tag create(TagCategory category, String value, String valueEn, String aliases, HubUserDetails user) {
        requireManager(user);
        String v = value == null ? "" : value.trim();
        if (category == null || v.isEmpty()) {
            throw new FormValidationException(Map.of("value", "validation.required"));
        }
        Tag conflict = findConflict(v, valueEn, aliases);
        if (conflict != null) {
            throw new TagDuplicateException(conflict);
        }
        Tag tag = tags.save(new Tag(category, v, blankToNull(valueEn), blankToNull(aliases)));
        audit.record(ENTITY, tag.getId(), AuditAction.CREATE, null, null, category + ": " + v, user.getUsername(), null);
        return tag;
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.TAGS, allEntries = true)
    public void setActive(Long id, boolean active, HubUserDetails user) {
        requireManager(user);
        Tag tag = tags.findById(id).orElseThrow();
        if (tag.isActive() != active) {
            audit.record(ENTITY, id, AuditAction.UPDATE, "active", tag.isActive(), active, user.getUsername(), null);
            tag.setActive(active);
        }
    }

    /** Где используется тег: число материалов, SKU и кейсов. */
    public record Usage(long artifacts, long skus, long cases) {
        public long total() {
            return artifacts + skus + cases;
        }
    }

    /** Использование всех тегов тремя запросами (для списка справочника, без запроса на каждый тег). */
    @Transactional(readOnly = true)
    public Map<Long, Usage> usageByTag() {
        Map<Long, long[]> acc = new java.util.HashMap<>();
        String[] tables = {"artifact_tag", "sku_tag", "case_tag"};
        for (int i = 0; i < tables.length; i++) {
            final int idx = i;
            jdbc.query("select tag_id, count(*) from " + tables[i] + " group by tag_id", rs -> {
                acc.computeIfAbsent(rs.getLong(1), k -> new long[3])[idx] = rs.getLong(2);
            });
        }
        Map<Long, Usage> result = new java.util.HashMap<>();
        acc.forEach((id, a) -> result.put(id, new Usage(a[0], a[1], a[2])));
        return result;
    }

    /**
     * Удаляет тег (мягко): снимает его со всех материалов, SKU и кейсов и скрывает. Что было привязано, записывается
     * в журнал изменений. Только продакты и администратор.
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.TAGS, allEntries = true)
    public Usage delete(Long id, HubUserDetails user) {
        requireManager(user);
        Tag tag = tags.findById(id).filter(t -> !t.isDeleted()).orElseThrow();
        Usage usage = usageByTag().getOrDefault(id, new Usage(0, 0, 0));
        String bound = "artifacts=" + ids("select artifact_id from artifact_tag where tag_id = ?", id)
                + "; skus=" + ids("select sku_id from sku_tag where tag_id = ?", id)
                + "; cases=" + ids("select case_id from case_tag where tag_id = ?", id);
        String info = tag.getCategory() + ": " + tag.getValue()
                + (tag.getValueEn() != null ? " / " + tag.getValueEn() : "")
                + (tag.getAliases() != null ? " (синонимы: " + tag.getAliases() + ")" : "");
        jdbc.update("delete from artifact_tag where tag_id = ?", id);
        jdbc.update("delete from sku_tag where tag_id = ?", id);
        jdbc.update("delete from case_tag where tag_id = ?", id);
        audit.record(ENTITY, id, AuditAction.DELETE, null, info + " | " + bound, null, user.getUsername(), null);
        tag.markDeleted(cut(info + " | " + bound));
        return usage;
    }

    private List<Long> ids(String sql, Long tagId) {
        return jdbc.queryForList(sql, Long.class, tagId);
    }

    private static String cut(String text) {
        return text.length() <= 500 ? text : text.substring(0, 497) + "...";
    }

    /** Ищет активный тег по значению, английскому названию или синониму (без учета регистра, дефисов и пробелов). */
    public Optional<Tag> resolve(String text, TagCategory preferred) {
        String key = normalize(text);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Tag> candidates = new LinkedHashMap<>();
        for (Tag t : active()) {
            if (preferred != null && t.getCategory() != preferred) {
                continue;
            }
            candidates.putIfAbsent(normalize(t.getValue()), t);
            if (t.getValueEn() != null) {
                candidates.putIfAbsent(normalize(t.getValueEn()), t);
            }
            t.getAliasList().forEach(a -> candidates.putIfAbsent(normalize(a), t));
        }
        return Optional.ofNullable(candidates.get(key));
    }

    /**
     * Ищет тег, который уже занимает это название: совпадение значения, английского названия или синонима с любым
     * существующим тегом (в том числе отключенным) без учета регистра, пробелов и дефисов.
     */
    public Tag findConflict(String value, String valueEn, String aliases) {
        java.util.Set<String> wanted = new java.util.LinkedHashSet<>();
        wanted.add(normalize(value));
        if (valueEn != null && !valueEn.isBlank()) {
            wanted.add(normalize(valueEn));
        }
        if (aliases != null) {
            for (String a : aliases.split(",")) {
                if (!a.isBlank()) {
                    wanted.add(normalize(a));
                }
            }
        }
        wanted.remove("");
        for (Tag t : all()) {
            java.util.Set<String> taken = new java.util.HashSet<>();
            taken.add(normalize(t.getValue()));
            if (t.getValueEn() != null) {
                taken.add(normalize(t.getValueEn()));
            }
            t.getAliasList().forEach(a -> taken.add(normalize(a)));
            for (String key : wanted) {
                if (taken.contains(key)) {
                    return t;
                }
            }
        }
        return null;
    }

    private void requireManager(HubUserDetails user) {
        if (!user.getRoles().contains(Role.PRODUCT) && !user.getRoles().contains(Role.ADMIN)) {
            throw new AccessDeniedException("Теги создают только PRODUCT и ADMIN");
        }
    }

    static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]+", "");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
