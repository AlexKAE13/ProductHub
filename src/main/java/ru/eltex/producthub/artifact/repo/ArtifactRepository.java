package ru.eltex.producthub.artifact.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.tag.domain.Tag;

public interface ArtifactRepository extends JpaRepository<Artifact, Long> {

    @Query("select a from Artifact a join fetch a.type join fetch a.owner left join fetch a.line "
            + "left join fetch a.currentVersion where a.id = :id and a.deleted = false")
    Optional<Artifact> findCardById(@Param("id") Long id);

    @Query("select a from Artifact a where a.sourceKey = :key and a.deleted = false")
    List<Artifact> findBySourceKey(@Param("key") String key);

    @Query("select a.id, s from Artifact a join a.skus s where a.id in :ids order by s.model")
    List<Object[]> findSkusByArtifactIds(@Param("ids") Collection<Long> ids);

    @Query("select a.id, t from Artifact a join a.tags t where a.id in :ids order by t.category, t.value")
    List<Object[]> findTagsByArtifactIds(@Param("ids") Collection<Long> ids);

    @Query("select t from Artifact a join a.tags t where a.id = :id order by t.category, t.value")
    List<Tag> findTags(@Param("id") Long id);

    @Query("select s.id from Artifact a join a.skus s where a.id = :id")
    List<Long> findSkuIds(@Param("id") Long id);

    /** Материалы SKU для расчета заполненности: язык и тип только актуальных материалов. */
    @Query("select a.language, a.type.name from Artifact a where a.deleted = false and a.status = "
            + "ru.eltex.producthub.artifact.domain.ArtifactStatus.ACTUAL and exists "
            + "(select s.id from a.skus s where s.id = :skuId)")
    List<Object[]> findActualLanguagesAndTypes(@Param("skuId") Long skuId);

    /** Есть ли у SKU материал того же типа на другом языке (пара RU и EN). */
    @Query("select count(a2) from Artifact a2 join a2.skus s where a2.type.id = :typeId and a2.language in :languages "
            + "and a2.deleted = false and s.id = :skuId and a2.id <> :excludeId")
    long countCounterparts(@Param("typeId") Long typeId, @Param("languages") java.util.Collection<ru.eltex.producthub.artifact.domain.Language> languages,
                           @Param("skuId") Long skuId, @Param("excludeId") Long excludeId);

    /** Артефакты, которые давно не ревьюили. Черновики и снятые не проверяются. */
    @Query("select a.id from Artifact a where a.deleted = false and a.reviewDueAt < :now and a.status not in "
            + "(ru.eltex.producthub.artifact.domain.ArtifactStatus.EOL, ru.eltex.producthub.artifact.domain.ArtifactStatus.DRAFT)")
    List<Long> findStaleIds(@Param("now") java.time.LocalDateTime now);
}
