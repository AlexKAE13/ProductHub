package ru.eltex.producthub.sku.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@EntityListeners(ru.eltex.producthub.common.IndexedChangeListener.class)
@Table(name = "spec_value")
public class SpecValue implements ru.eltex.producthub.common.Indexable {

    @Override
    public String indexType() {
        return "SKU";
    }

    @Override
    public Long indexId() {
        return sku == null ? null : sku.getId();
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id")
    private Sku sku;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id")
    private SpecDefinition definition;

    @Column(name = "spec_value", nullable = false)
    private String value;

    protected SpecValue() {
    }

    public SpecValue(Sku sku, SpecDefinition definition, String value) {
        this.sku = sku;
        this.definition = definition;
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public Sku getSku() {
        return sku;
    }

    public SpecDefinition getDefinition() {
        return definition;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
