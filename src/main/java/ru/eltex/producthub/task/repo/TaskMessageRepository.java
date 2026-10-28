package ru.eltex.producthub.task.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.eltex.producthub.task.domain.TaskMessage;

public interface TaskMessageRepository extends JpaRepository<TaskMessage, Long> {

    /** Последние сообщения чата доски (новые первыми, автор подгружается сразу). */
    @Query("select m from TaskMessage m join fetch m.author where m.boardId = :boardId and m.taskId is null and m.deleted = false "
            + "order by m.createdAt desc, m.id desc")
    List<TaskMessage> boardChat(@Param("boardId") Long boardId, Pageable page);

    @Query("select m from TaskMessage m join fetch m.author where m.taskId = :taskId and m.deleted = false "
            + "order by m.createdAt desc, m.id desc")
    List<TaskMessage> cardChat(@Param("taskId") Long taskId, Pageable page);

    @Query("select m from TaskMessage m join fetch m.author where m.id = :id")
    Optional<TaskMessage> findWithAuthor(@Param("id") Long id);
}
