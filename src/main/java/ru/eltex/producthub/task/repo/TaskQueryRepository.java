package ru.eltex.producthub.task.repo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.springframework.stereotype.Repository;

import ru.eltex.producthub.sku.service.ModelNormalizer;
import ru.eltex.producthub.task.domain.Task;
import ru.eltex.producthub.task.domain.TaskType;

@Repository
public class TaskQueryRepository {

    @PersistenceContext
    private EntityManager em;

    /** Задачи доски одним запросом: исполнитель и SKU подгружаются сразу. Закрытые - только свежие. */
    public List<Task> board(Long assigneeId, TaskType type, String sku, Long line, LocalDateTime doneSince) {
        return board(null, false, assigneeId, type, sku, line, doneSince);
    }

    /**
     * Задачи доски: boardId пустой - системная доска "Входящие запросы". archived - показать архив вместо живых задач.
     * doneSince пустой - закрытые показываются все (на своих досках их убирает архив, а не срок).
     */
    public List<Task> board(Long boardId, boolean archived, Long assigneeId, TaskType type, String sku, Long line,
                            LocalDateTime doneSince) {
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (boardId == null) {
            where.add("t.board is null");
        } else {
            where.add("t.board.id = :board");
            params.put("board", boardId);
        }
        where.add(archived ? "t.archived = true" : "t.archived = false");
        if (doneSince != null && !archived) {
            where.add("(t.status <> ru.eltex.producthub.task.domain.TaskStatus.DONE or t.closedAt >= :since)");
            params.put("since", doneSince);
        }
        if (assigneeId != null) {
            where.add("a.id = :assignee");
            params.put("assignee", assigneeId);
        }
        if (type != null) {
            where.add("t.type = :type");
            params.put("type", type);
        }
        if (sku != null && !sku.isBlank()) {
            where.add("s.modelNormalized like :sku");
            params.put("sku", "%" + ModelNormalizer.normalize(sku) + "%");
        }
        if (line != null) {
            where.add("s.line.id = :line");
            params.put("line", line);
        }
        TypedQuery<Task> query = em.createQuery("select t from Task t join fetch t.assignee a left join fetch t.sku s where "
                + String.join(" and ", where) + " order by " + (archived ? "t.archivedAt desc nulls last, t.id desc" : "t.dueDate asc nulls last, t.id desc"), Task.class);
        params.forEach(query::setParameter);
        query.setMaxResults(1000);
        return query.getResultList();
    }
}
