package ru.eltex.producthub.task.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.eltex.producthub.task.domain.TaskBoard;

public interface TaskBoardRepository extends JpaRepository<TaskBoard, Long> {

    /** Доска со всем нужным для показа: владелец, участники и колонки одним заходом. */
    @Query("select distinct b from TaskBoard b join fetch b.owner left join fetch b.members where b.id = :id and b.deleted = false")
    Optional<TaskBoard> findActiveWithMembers(@Param("id") Long id);

    @Query("select distinct b from TaskBoard b join fetch b.owner join b.members m where m.id = :userId and b.deleted = false order by b.name")
    List<TaskBoard> findForMember(@Param("userId") Long userId);

    @Query("select count(b) > 0 from TaskBoard b join b.members m where b.id = :boardId and m.id = :userId and b.deleted = false")
    boolean isMember(@Param("boardId") Long boardId, @Param("userId") Long userId);

    @Query("select count(b) > 0 from TaskBoard b where b.id = :boardId and b.owner.id = :userId and b.deleted = false")
    boolean isOwner(@Param("boardId") Long boardId, @Param("userId") Long userId);
}
