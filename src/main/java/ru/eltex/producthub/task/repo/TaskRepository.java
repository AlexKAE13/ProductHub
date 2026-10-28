package ru.eltex.producthub.task.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.eltex.producthub.task.domain.RelatedType;
import ru.eltex.producthub.task.domain.Task;
import ru.eltex.producthub.task.domain.TaskStatus;
import ru.eltex.producthub.task.domain.TaskType;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("select t from Task t join fetch t.assignee left join fetch t.sku left join fetch t.board where t.id = :id")
    Optional<Task> findDetailed(@Param("id") Long id);

    /** Открытая задача того же типа на тот же объект и SKU: защита от дублей (SPEC.md 4.9). */
    @Query("select count(t) from Task t where t.type = :type and t.relatedType = :rt and t.relatedId = :rid "
            + "and t.status <> ru.eltex.producthub.task.domain.TaskStatus.DONE and t.sku.id = :skuId and t.board is null")
    long countOpenForSku(@Param("type") TaskType type, @Param("rt") RelatedType rt, @Param("rid") Long rid,
                         @Param("skuId") Long skuId);

    @Query("select count(t) from Task t where t.type = :type and t.relatedType = :rt and t.relatedId = :rid "
            + "and t.status <> ru.eltex.producthub.task.domain.TaskStatus.DONE and t.sku is null")
    long countOpenWithoutSku(@Param("type") TaskType type, @Param("rt") RelatedType rt, @Param("rid") Long rid);

    @Query("select count(t) from Task t where t.type = :type and t.relatedType = :rt and t.relatedId = :rid "
            + "and t.status <> ru.eltex.producthub.task.domain.TaskStatus.DONE and t.title = :title")
    long countOpenByTitle(@Param("type") TaskType type, @Param("rt") RelatedType rt, @Param("rid") Long rid,
                          @Param("title") String title);

    @Query("select t from Task t where t.relatedType = :rt and t.relatedId = :rid "
            + "and t.status <> ru.eltex.producthub.task.domain.TaskStatus.DONE")
    List<Task> findOpenByRelated(@Param("rt") RelatedType rt, @Param("rid") Long rid);

    @Query("select t from Task t where t.type = :type and t.status <> ru.eltex.producthub.task.domain.TaskStatus.DONE")
    List<Task> findOpenOfType(@Param("type") TaskType type);

    /** Новые задачи на системной доске (для счетчика в меню). */
    @Query("select count(t) from Task t where t.assignee.id = :userId and t.status = :status and t.board is null and t.archived = false")
    long countNewInbox(@Param("userId") Long userId, @Param("status") TaskStatus status);

    @Query("select t from Task t where t.board.id = :boardId and t.assignee.id = :userId")
    List<Task> findOnBoardAssignedTo(@Param("boardId") Long boardId, @Param("userId") Long userId);
}
