package ru.eltex.producthub.sku.domain;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import ru.eltex.producthub.security.Role;

@Entity
@Table(name = "spec_definition")
public class SpecDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spec_key", nullable = false, unique = true)
    private String key;

    @Column(name = "label_ru", nullable = false)
    private String labelRu;

    @Column(name = "label_en", nullable = false)
    private String labelEn;

    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false)
    private SpecValueType valueType;

    @Column(name = "enum_values")
    private String enumValues;

    @Column(name = "group_name", nullable = false)
    private String group;

    @Column(name = "applies_to_categories", nullable = false)
    private String appliesToCategories;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "editable_by", nullable = false)
    private Role editableBy;

    protected SpecDefinition() {
    }

    public Long getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getLabelRu() {
        return labelRu;
    }

    public String getLabelEn() {
        return labelEn;
    }

    public String getUnit() {
        return unit;
    }

    public SpecValueType getValueType() {
        return valueType;
    }

    public List<String> getEnumOptions() {
        return enumValues == null || enumValues.isBlank() ? List.of() : Arrays.asList(enumValues.split(","));
    }

    public String getGroup() {
        return group;
    }

    public String getAppliesToCategories() {
        return appliesToCategories;
    }

    public boolean appliesTo(String category) {
        return Arrays.asList(appliesToCategories.split(",")).contains(category);
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Role getEditableBy() {
        return editableBy;
    }
}
