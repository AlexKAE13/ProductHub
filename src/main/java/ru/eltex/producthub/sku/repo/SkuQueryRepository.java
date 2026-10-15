package ru.eltex.producthub.sku.repo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.springframework.stereotype.Repository;

import ru.eltex.producthub.sku.domain.LifecycleStatus;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.service.CatalogFilter;
import ru.eltex.producthub.sku.service.CatalogPage;
import ru.eltex.producthub.sku.service.ModelNormalizer;

/** Запрос каталога с фильтрами. Линейка и владелец подтягиваются одним запросом (fetch join), без N+1. */
@Repository
public class SkuQueryRepository {

    private static final Map<String, String> SORTS = Map.of(
            "model", "s.model",
            "name", "s.nameRu",
            "line", "l.code",
            "category", "s.category",
            "status", "s.lifecycleStatus",
            "owner", "o.fullName",
            "completeness", "s.completeness",
            "review", "s.lastReviewDate");

    @PersistenceContext
    private EntityManager em;

    public CatalogPage search(CatalogFilter f, boolean externalOnly) {
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        where.add("s.deleted = false");
        if (externalOnly) {
            where.add("s.contour = ru.eltex.producthub.sku.domain.Contour.EXTERNAL");
        }
        if (notBlank(f.family())) {
            where.add("l.family = :family");
            params.put("family", f.family());
        }
        if (notBlank(f.line())) {
            where.add("l.code = :line");
            params.put("line", f.line());
        }
        if (notBlank(f.category())) {
            where.add("s.category = :category");
            params.put("category", f.category());
        }
        LifecycleStatus status = parseStatus(f.status());
        if (status != null) {
            where.add("s.lifecycleStatus = :status");
            params.put("status", status);
        } else if (!f.includeEol()) {
            where.add("s.lifecycleStatus <> ru.eltex.producthub.sku.domain.LifecycleStatus.EOL");
        }
        if (f.owner() != null) {
            where.add("exists (select ow.id from s.owners ow where ow.id = :owner)");
            params.put("owner", f.owner());
        }
        if (f.tag() != null) {
            where.add("exists (select g.id from s.tags g where g.id = :tag)");
            params.put("tag", f.tag());
        }
        if (f.reviewDue()) {
            where.add("s.nextReviewDate < :today");
            params.put("today", LocalDate.now());
        }
        if (notBlank(f.q())) {
            where.add("(lower(s.model) like :q or s.modelNormalized like :qn or lower(s.nameRu) like :q "
                    + "or lower(s.nameEn) like :q or lower(s.siteSubsectionRu) like :q)");
            params.put("q", "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%");
            params.put("qn", "%" + ModelNormalizer.normalize(f.q()) + "%");
        }
        String clause = " where " + String.join(" and ", where);

        TypedQuery<Long> count = em.createQuery(
                "select count(s) from Sku s join s.line l join s.owner o" + clause, Long.class);
        params.forEach(count::setParameter);
        long total = count.getSingleResult();

        String sortExpr = SORTS.getOrDefault(f.sort() == null ? "model" : f.sort(), "s.model");
        String dir = "desc".equalsIgnoreCase(f.dir()) ? "desc" : "asc";
        TypedQuery<Sku> query = em.createQuery("select s from Sku s join fetch s.line l join fetch s.owner o"
                + clause + " order by " + sortExpr + " " + dir + ", s.id", Sku.class);
        params.forEach(query::setParameter);
        int page = Math.max(0, f.page());
        query.setFirstResult(page * CatalogFilter.PAGE_SIZE);
        query.setMaxResults(CatalogFilter.PAGE_SIZE);
        List<Sku> found = query.getResultList();
        if (!found.isEmpty()) {
            // Владельцы всей страницы одним запросом, чтобы шаблон не ходил в базу по каждой строке
            em.createQuery("select distinct s from Sku s left join fetch s.owners where s in :list", Sku.class)
                    .setParameter("list", found).getResultList();
        }
        return new CatalogPage(found, total, page, CatalogFilter.PAGE_SIZE);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static LifecycleStatus parseStatus(String s) {
        if (!notBlank(s)) {
            return null;
        }
        try {
            return LifecycleStatus.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
