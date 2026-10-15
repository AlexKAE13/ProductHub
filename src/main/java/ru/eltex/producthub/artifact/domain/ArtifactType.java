package ru.eltex.producthub.artifact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import ru.eltex.producthub.sku.domain.Contour;

@Entity
@Table(name = "artifact_type")
public class ArtifactType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private int importance = 3;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_contour", nullable = false)
    private Contour defaultContour = Contour.INTERNAL;

    private String producer;
    private String consumer;

    @Column(name = "review_period_days", nullable = false)
    private int reviewPeriodDays = 90;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_block", nullable = false)
    private CardBlock cardBlock = CardBlock.DOCS;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ArtifactType() {
    }

    public ArtifactType(String name, int importance, Contour defaultContour, String producer, String consumer,
                        int reviewPeriodDays, CardBlock cardBlock, int sortOrder) {
        this.name = name;
        this.importance = importance;
        this.defaultContour = defaultContour;
        this.producer = producer;
        this.consumer = consumer;
        this.reviewPeriodDays = reviewPeriodDays;
        this.cardBlock = cardBlock;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getImportance() {
        return importance;
    }

    public Contour getDefaultContour() {
        return defaultContour;
    }

    public String getProducer() {
        return producer;
    }

    public String getConsumer() {
        return consumer;
    }

    public int getReviewPeriodDays() {
        return reviewPeriodDays;
    }

    public CardBlock getCardBlock() {
        return cardBlock;
    }

    public boolean isShowInSkuCard() {
        return cardBlock != CardBlock.NONE;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
