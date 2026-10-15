package ru.eltex.producthub.sku.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.eltex.producthub.sku.domain.Sku;

public interface SkuRepository extends JpaRepository<Sku, Long> {

    @Query("select s from Sku s join fetch s.line join fetch s.owner left join fetch s.owners left join fetch s.successor "
            + "where s.modelNormalized = :normalized and s.deleted = false")
    Optional<Sku> findCardByNormalized(@Param("normalized") String normalized);

    @Query("select s from Sku s join fetch s.line join fetch s.owner left join fetch s.owners left join fetch s.successor "
            + "where s.id = :id and s.deleted = false")
    Optional<Sku> findCardById(@Param("id") Long id);

    Optional<Sku> findByModelNormalized(String modelNormalized);

    @Query("select s from Sku s join fetch s.line where s.successor.id = :id and s.deleted = false order by s.model")
    List<Sku> findPredecessors(@Param("id") Long id);

    @Query("select s from Sku s join fetch s.owner where s.deleted = false")
    List<Sku> findAllActiveWithOwner();

    @Query("select t from Sku s join s.tags t where s.id = :id order by t.category, t.value")
    List<ru.eltex.producthub.tag.domain.Tag> findTags(@Param("id") Long id);

    @Query("select s.id from Sku s where s.deleted = false and s.nextReviewDate < :today and s.lifecycleStatus <> "
            + "ru.eltex.producthub.sku.domain.LifecycleStatus.EOL")
    List<Long> findReviewDueIds(@Param("today") java.time.LocalDate today);

    long countByDeletedFalse();

    @Query("select distinct s.category from Sku s where s.deleted = false order by s.category")
    List<String> findCategories();
}
