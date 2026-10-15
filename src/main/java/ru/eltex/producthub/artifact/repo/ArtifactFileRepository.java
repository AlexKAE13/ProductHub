package ru.eltex.producthub.artifact.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.eltex.producthub.artifact.domain.ArtifactFile;

public interface ArtifactFileRepository extends JpaRepository<ArtifactFile, Long> {

    @Query("select f from ArtifactFile f join fetch f.version v join fetch v.artifact a join fetch a.type "
            + "where f.id = :id")
    Optional<ArtifactFile> findForDownload(@Param("id") Long id);

    @Query("select f from ArtifactFile f where f.version.id in :versionIds order by f.role, f.id")
    List<ArtifactFile> findByVersionIds(@Param("versionIds") Collection<Long> versionIds);

    /** Где уже лежит файл с таким содержимым (для предупреждения о дубле). */
    @Query("select f from ArtifactFile f join fetch f.version v join fetch v.artifact a "
            + "where f.sha256 = :sha and a.deleted = false order by f.id")
    List<ArtifactFile> findBySha256(@Param("sha") String sha);
}
