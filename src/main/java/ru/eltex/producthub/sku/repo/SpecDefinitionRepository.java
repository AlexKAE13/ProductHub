package ru.eltex.producthub.sku.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.eltex.producthub.sku.domain.SpecDefinition;

public interface SpecDefinitionRepository extends JpaRepository<SpecDefinition, Long> {

    List<SpecDefinition> findAllByOrderBySortOrderAsc();
}
