package ru.eltex.producthub.artifact.repo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.springframework.stereotype.Repository;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.artifact.domain.ArtifactStatus;
import ru.eltex.producthub.artifact.domain.Language;
import ru.eltex.producthub.artifact.service.ArtifactFilter;
import ru.eltex.producthub.artifact.service.ArtifactPage;
import ru.eltex.producthub.sku.domain.Contour;
import ru.eltex.producthub.sku.service.ModelNormalizer;

/** Запросы списка артефактов и материалов карточки SKU. Связанные сущности загружаются fetch join-ом. */
@Repository
public class ArtifactQueryRepository {

    private static final Map<String, String> SORTS = Map.of(
            "title", "a.title",
            "type", "t.sortOrder",
            "language", "a.language",
            "status", "a.status",
            "owner", "o.fullName",
            "updated", "a.updatedAt",
            "review", "a.reviewDueAt");

    @PersistenceContext
    private EntityManager em;

    public ArtifactPage search(ArtifactFilter f, boolean externalOnly) {
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        where.add("a.deleted = false");
        if (externalOnly) {
            where.add("a.contour = ru.eltex.producthub.sku.domain.Contour.EXTERNAL");
        }
        if (f.type() != null) {
            where.add("t.id = :type");
            params.put("type", f.type());
        }
        Language language = parse(Language.class, f.language());
        if (language != null) {
            where.add("a.language = :language");
            params.put("language", language);
        }
        ArtifactStatus status = parse(ArtifactStatus.class, f.status());
        if (status != null) {
            where.add("a.status = :status");
            params.put("status", status);
        }
        Contour contour = parse(Contour.class, f.contour());
        if (contour != null) {
            where.add("a.contour = :contour");
            params.put("contour", contour);
        }
        if (f.owner() != null) {
            where.add("o.id = :owner");
            params.put("owner", f.owner());
        }
        if (f.line() != null) {
            where.add("(a.line.id = :line or exists (select s.id from a.skus s where s.line.id = :line))");
            params.put("line", f.line());
        }
        if (f.family() != null && !f.family().isBlank()) {
            // семейство - это группа линеек: материал подходит, если его линейка или линейка любого его SKU из этого семейства
            where.add("(a.line.family = :family or exists (select s.id from a.skus s where s.line.family = :family))");
            params.put("family", f.family());
        }
        if (f.sku() != null && !f.sku().isBlank()) {
            where.add("exists (select s.id from a.skus s where s.modelNormalized like :sku)");
            params.put("sku", "%" + ModelNormalizer.normalize(f.sku()) + "%");
        }
        if (f.tag() != null) {
            where.add("exists (select g.id from a.tags g where g.id = :tag)");
            params.put("tag", f.tag());
        }
        if (f.reviewDue()) {
            where.add("a.reviewDueAt < :now");
            params.put("now", LocalDateTime.now());
        }
        if (f.q() != null && !f.q().isBlank()) {
            where.add("(lower(a.title) like :q or lower(a.description) like :q)");
            params.put("q", "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%");
        }
        String clause = " where " + String.join(" and ", where);
        String from = " from Artifact a join a.type t join a.owner o";

        TypedQuery<Long> count = em.createQuery("select count(a)" + from + clause, Long.class);
        params.forEach(count::setParameter);
        long total = count.getSingleResult();

        String sortExpr = SORTS.getOrDefault(f.sort() == null ? "updated" : f.sort(), "a.updatedAt");
        String dir = "asc".equalsIgnoreCase(f.dir()) ? "asc" : "desc";
        TypedQuery<Artifact> query = em.createQuery("select a from Artifact a join fetch a.type t join fetch a.owner o "
                + "left join fetch a.line left join fetch a.currentVersion" + clause
                + " order by " + sortExpr + " " + dir + ", a.id desc", Artifact.class);
        params.forEach(query::setParameter);
        int page = Math.max(0, f.page());
        query.setFirstResult(page * ArtifactFilter.PAGE_SIZE);
        query.setMaxResults(ArtifactFilter.PAGE_SIZE);
        return new ArtifactPage(query.getResultList(), total, page, ArtifactFilter.PAGE_SIZE);
    }

    /** Материалы, показываемые в карточке SKU: привязанные к SKU и к его линейке целиком. */
    public List<Artifact> findForSkuCard(Long skuId, Long lineId, boolean externalOnly) {
        String external = externalOnly ? " and a.contour = ru.eltex.producthub.sku.domain.Contour.EXTERNAL" : "";
        return em.createQuery("select a from Artifact a join fetch a.type t join fetch a.owner "
                + "left join fetch a.currentVersion where a.deleted = false "
                + "and t.cardBlock <> ru.eltex.producthub.artifact.domain.CardBlock.NONE"
                + " and a.status <> ru.eltex.producthub.artifact.domain.ArtifactStatus.DRAFT"
                + external
                + " and (a.line.id = :lineId or exists (select s.id from a.skus s where s.id = :skuId))"
                + " order by t.sortOrder, a.language, a.title", Artifact.class)
                .setParameter("skuId", skuId)
                .setParameter("lineId", lineId)
                .getResultList();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
