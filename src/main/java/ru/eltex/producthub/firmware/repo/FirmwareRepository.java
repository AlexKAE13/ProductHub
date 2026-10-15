package ru.eltex.producthub.firmware.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.eltex.producthub.firmware.domain.Firmware;

public interface FirmwareRepository extends JpaRepository<Firmware, Long> {

    @Query("select f from Firmware f where f.id = :id and f.deleted = false")
    Optional<Firmware> findActiveById(@Param("id") Long id);

    @Query("select f from Firmware f where f.deleted = false and exists "
            + "(select s.id from f.skus s where s.id = :skuId) order by f.releaseDate desc, f.id desc")
    List<Firmware> findAllForSku(@Param("skuId") Long skuId);

    @Query("select f.id, s from Firmware f join f.skus s where f.id in :ids order by s.model")
    List<Object[]> findSkusByFirmwareIds(@Param("ids") Collection<Long> ids);

    @Query("select s.id from Firmware f join f.skus s where f.id = :id")
    List<Long> findSkuIds(@Param("id") Long id);

    /** Все RELEASE-прошивки для набора SKU, новые первыми: первая у каждого SKU и есть текущая. */
    @Query("select s.id, f from Firmware f join f.skus s where s.id in :skuIds and f.status = "
            + "ru.eltex.producthub.firmware.domain.FirmwareStatus.RELEASE and f.deleted = false "
            + "order by f.releaseDate desc, f.id desc")
    List<Object[]> findReleasesForSkus(@Param("skuIds") Collection<Long> skuIds);
}
