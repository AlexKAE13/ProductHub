package ru.eltex.producthub.importer;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ImportRunRepository extends JpaRepository<ImportRun, Long> {

    @Query("select r from ImportRun r order by r.startedAt desc, r.id desc")
    List<ImportRun> findLatest(Pageable pageable);
}
