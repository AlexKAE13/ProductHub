package ru.eltex.producthub.sku.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.eltex.producthub.sku.domain.ProductLine;

public interface ProductLineRepository extends JpaRepository<ProductLine, Long> {

    Optional<ProductLine> findByCode(String code);

    List<ProductLine> findAllByOrderBySortOrderAscCodeAsc();
}
