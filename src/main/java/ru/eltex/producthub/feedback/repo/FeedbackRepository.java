package ru.eltex.producthub.feedback.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.eltex.producthub.feedback.domain.FeedbackComment;

public interface FeedbackRepository extends JpaRepository<FeedbackComment, Long> {

    List<FeedbackComment> findByPagePathAndDeletedFalseOrderByIdAsc(String pagePath);

    List<FeedbackComment> findByDeletedFalseOrderByPagePathAscIdAsc();
}
