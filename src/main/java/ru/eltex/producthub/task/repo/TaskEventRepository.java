package ru.eltex.producthub.task.repo;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.eltex.producthub.task.domain.TaskEvent;

public interface TaskEventRepository extends JpaRepository<TaskEvent, Long> {

    @Query("select e from TaskEvent e join fetch e.actor where e.boardId = :boardId order by e.createdAt desc, e.id desc")
    List<TaskEvent> forBoard(@Param("boardId") Long boardId, Pageable page);

    @Query("select e from TaskEvent e join fetch e.actor where e.taskId = :taskId order by e.createdAt desc, e.id desc")
    List<TaskEvent> forTask(@Param("taskId") Long taskId, Pageable page);
}
