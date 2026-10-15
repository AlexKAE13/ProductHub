package ru.eltex.producthub.casestudy.repo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.springframework.stereotype.Repository;

import ru.eltex.producthub.casestudy.domain.CaseStudy;
import ru.eltex.producthub.casestudy.service.CaseFilter;
import ru.eltex.producthub.casestudy.service.CasePage;
import ru.eltex.producthub.sku.service.ModelNormalizer;

@Repository
public class CaseQueryRepository {

    @PersistenceContext
    private EntityManager em;

    public CasePage search(CaseFilter f, boolean publicOnly) {
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        where.add("c.deleted = false");
        if (publicOnly) {
            where.add("c.publicationAllowed = true");
        }
        if (f.country() != null && !f.country().isBlank()) {
            where.add("c.country = :country");
            params.put("country", f.country());
        }
        if (f.year() != null) {
            where.add("c.year = :year");
            params.put("year", f.year());
        }
        if (f.tag() != null) {
            where.add("exists (select g.id from c.tags g where g.id = :tag)");
            params.put("tag", f.tag());
        }
        if (f.sku() != null && !f.sku().isBlank()) {
            where.add("exists (select s.id from c.skus s where s.modelNormalized like :sku)");
            params.put("sku", "%" + ModelNormalizer.normalize(f.sku()) + "%");
        }
        if (f.q() != null && !f.q().isBlank()) {
            where.add("(lower(c.title) like :q or lower(c.summary) like :q or lower(c.city) like :q)");
            params.put("q", "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%");
        }
        String clause = " where " + String.join(" and ", where);
        TypedQuery<Long> count = em.createQuery("select count(c) from CaseStudy c" + clause, Long.class);
        params.forEach(count::setParameter);
        long total = count.getSingleResult();
        TypedQuery<CaseStudy> query = em.createQuery("select c from CaseStudy c join fetch c.owner" + clause
                + " order by c.year desc, c.id desc", CaseStudy.class);
        params.forEach(query::setParameter);
        int page = Math.max(0, f.page());
        query.setFirstResult(page * CaseFilter.PAGE_SIZE);
        query.setMaxResults(CaseFilter.PAGE_SIZE);
        return new CasePage(query.getResultList(), total, page, CaseFilter.PAGE_SIZE);
    }
}
