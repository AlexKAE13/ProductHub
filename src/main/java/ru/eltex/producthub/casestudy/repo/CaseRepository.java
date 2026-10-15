package ru.eltex.producthub.casestudy.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.casestudy.domain.CaseStudy;
import ru.eltex.producthub.tag.domain.Tag;

public interface CaseRepository extends JpaRepository<CaseStudy, Long> {

    @Query("select c from CaseStudy c join fetch c.owner where c.id = :id and c.deleted = false")
    Optional<CaseStudy> findActiveById(@Param("id") Long id);

    @Query("select c from CaseStudy c join fetch c.owner where c.deleted = false and exists "
            + "(select s.id from c.skus s where s.id = :skuId) order by c.year desc, c.id desc")
    List<CaseStudy> findForSku(@Param("skuId") Long skuId);

    @Query("select c.id, s from CaseStudy c join c.skus s where c.id in :ids order by s.model")
    List<Object[]> findSkusByCaseIds(@Param("ids") Collection<Long> ids);

    @Query("select c.id, t from CaseStudy c join c.tags t where c.id in :ids order by t.category, t.value")
    List<Object[]> findTagsByCaseIds(@Param("ids") Collection<Long> ids);

    @Query("select t from CaseStudy c join c.tags t where c.id = :id order by t.category, t.value")
    List<Tag> findTags(@Param("id") Long id);

    @Query("select a from CaseStudy c join c.artifacts a join fetch a.type join fetch a.currentVersion where c.id = :id and a.deleted = false order by a.id")
    List<Artifact> findAttachments(@Param("id") Long id);

    @Query("select s.id from CaseStudy c join c.skus s where c.id = :id")
    List<Long> findSkuIds(@Param("id") Long id);

    @Query("select distinct c.country from CaseStudy c where c.deleted = false order by c.country")
    List<String> findCountries();

    @Query("select distinct c.year from CaseStudy c where c.deleted = false and c.year is not null order by c.year desc")
    List<Integer> findYears();
}
