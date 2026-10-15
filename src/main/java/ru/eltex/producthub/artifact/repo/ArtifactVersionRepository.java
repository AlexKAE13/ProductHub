package ru.eltex.producthub.artifact.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.eltex.producthub.artifact.domain.ArtifactVersion;

public interface ArtifactVersionRepository extends JpaRepository<ArtifactVersion, Long> {

    List<ArtifactVersion> findByArtifactIdOrderByVersionNoDesc(Long artifactId);

    @Query("select coalesce(max(v.versionNo), 0) from ArtifactVersion v where v.artifact.id = :id")
    int maxVersionNo(@Param("id") Long id);
}
