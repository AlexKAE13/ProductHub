package ru.eltex.producthub.casestudy.service;

import java.util.List;
import java.util.Map;

import ru.eltex.producthub.casestudy.domain.CaseStudy;
import ru.eltex.producthub.tag.domain.Tag;

/** Кейсы SKU для блока 7 карточки. */
public record SkuCases(List<CaseStudy> cases, Map<Long, List<Tag>> tags, boolean canCreate) {

    public boolean isEmpty() {
        return cases.isEmpty();
    }
}
