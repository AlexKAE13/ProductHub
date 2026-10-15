package ru.eltex.producthub.artifact.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.eltex.producthub.artifact.domain.ArtifactType;

public interface ArtifactTypeRepository extends JpaRepository<ArtifactType, Long> {

    List<ArtifactType> findAllByOrderBySortOrderAscNameAsc();

    Optional<ArtifactType> findByName(String name);
}
