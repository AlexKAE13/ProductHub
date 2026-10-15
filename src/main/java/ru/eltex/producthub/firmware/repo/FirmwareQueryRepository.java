package ru.eltex.producthub.firmware.repo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.springframework.stereotype.Repository;

import ru.eltex.producthub.firmware.domain.Firmware;
import ru.eltex.producthub.firmware.domain.FirmwareStatus;
import ru.eltex.producthub.firmware.service.FirmwareFilter;
import ru.eltex.producthub.firmware.service.FirmwarePage;
import ru.eltex.producthub.sku.service.ModelNormalizer;

@Repository
public class FirmwareQueryRepository {

    @PersistenceContext
    private EntityManager em;

    public FirmwarePage search(FirmwareFilter f, boolean publicOnly) {
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        where.add("f.deleted = false");
        if (publicOnly) {
            where.add("f.publicRelease = true");
        }
        FirmwareStatus status = parse(f.status());
        if (status != null) {
            where.add("f.status = :status");
            params.put("status", status);
        }
        if (f.line() != null) {
            where.add("exists (select s.id from f.skus s where s.line.id = :line)");
            params.put("line", f.line());
        }
        if (f.sku() != null && !f.sku().isBlank()) {
            where.add("exists (select s.id from f.skus s where s.modelNormalized like :sku)");
            params.put("sku", "%" + ModelNormalizer.normalize(f.sku()) + "%");
        }
        if (f.q() != null && !f.q().isBlank()) {
            where.add("(lower(f.version) like :q or lower(f.releaseNotes) like :q)");
            params.put("q", "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%");
        }
        String clause = " where " + String.join(" and ", where);
        TypedQuery<Long> count = em.createQuery("select count(f) from Firmware f" + clause, Long.class);
        params.forEach(count::setParameter);
        long total = count.getSingleResult();
        TypedQuery<Firmware> query = em.createQuery("select f from Firmware f" + clause
                + " order by f.releaseDate desc, f.id desc", Firmware.class);
        params.forEach(query::setParameter);
        int page = Math.max(0, f.page());
        query.setFirstResult(page * FirmwareFilter.PAGE_SIZE);
        query.setMaxResults(FirmwareFilter.PAGE_SIZE);
        return new FirmwarePage(query.getResultList(), total, page, FirmwareFilter.PAGE_SIZE);
    }

    private static FirmwareStatus parse(String s) {
        try {
            return s == null || s.isBlank() ? null : FirmwareStatus.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
