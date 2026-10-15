package ru.eltex.producthub.tag.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.eltex.producthub.tag.domain.Tag;
import ru.eltex.producthub.tag.domain.TagCategory;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findAllByDeletedFalseOrderByCategoryAscValueAsc();

    Optional<Tag> findByCategoryAndValue(TagCategory category, String value);
}
