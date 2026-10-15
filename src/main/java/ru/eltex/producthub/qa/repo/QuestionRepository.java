package ru.eltex.producthub.qa.repo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.eltex.producthub.qa.domain.Question;
import ru.eltex.producthub.qa.domain.TargetType;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    @Query("select q from Question q join fetch q.author left join fetch q.answeredBy where q.id = :id")
    Optional<Question> findDetailed(@Param("id") Long id);

    @Query("select q from Question q join fetch q.author left join fetch q.answeredBy "
            + "where q.targetType = :type and q.targetId = :id order by q.createdAt desc")
    List<Question> findByTarget(@Param("type") TargetType type, @Param("id") Long id, Pageable pageable);

    @Query("select q from Question q left join fetch q.answeredBy where q.author.id = :authorId "
            + "order by q.createdAt desc")
    List<Question> findByAuthor(@Param("authorId") Long authorId, Pageable pageable);

    /** Отвечено и есть ответ: кандидаты для подсказки "Возможно, ответ уже есть". */
    @Query("select q from Question q where q.kind = ru.eltex.producthub.qa.domain.QuestionKind.QUESTION "
            + "and q.answer is not null and q.status in "
            + "(ru.eltex.producthub.qa.domain.QuestionStatus.ANSWERED, ru.eltex.producthub.qa.domain.QuestionStatus.CLOSED) "
            + "order by q.answeredAt desc")
    List<Question> findAnswered(Pageable pageable);

    @Query("select count(q) from Question q where q.kind = ru.eltex.producthub.qa.domain.QuestionKind.QUESTION "
            + "and q.status in (ru.eltex.producthub.qa.domain.QuestionStatus.NEW, ru.eltex.producthub.qa.domain.QuestionStatus.IN_PROGRESS) "
            + "and q.createdAt < :before")
    long countOpenOlderThan(@Param("before") LocalDateTime before);
}
