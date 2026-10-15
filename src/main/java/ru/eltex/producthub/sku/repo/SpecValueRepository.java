package ru.eltex.producthub.sku.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.eltex.producthub.sku.domain.SpecValue;

public interface SpecValueRepository extends JpaRepository<SpecValue, Long> {

    @Query("select v from SpecValue v join fetch v.definition where v.sku.id = :skuId")
    List<SpecValue> findBySkuId(@Param("skuId") Long skuId);
}
