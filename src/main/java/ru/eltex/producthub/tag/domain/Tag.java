package ru.eltex.producthub.tag.domain;

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

@Entity
@Table(name = "tag")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TagCategory category;

    @Column(name = "tag_value", nullable = false)
    private String value;

    @Column(name = "value_en")
    private String valueEn;

    private String aliases;

    @Column(name = "norm_key", nullable = false)
    private String normKey;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "deleted_info")
    private String deletedInfo;

    protected Tag() {
    }

    public Tag(TagCategory category, String value, String valueEn, String aliases) {
        this.category = category;
        this.value = value;
        this.valueEn = valueEn;
        this.aliases = aliases;
        this.normKey = normalizeKey(value);
    }

    /** Ключ сравнения тегов: без регистра, пробелов, дефисов и подчеркиваний (Tag, tAg и TAG - одно и то же). */
    public static String normalizeKey(String text) {
        return text == null ? "" : text.toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s_\\-]+", "");
    }

    public String getNormKey() {
        return normKey;
    }

    public Long getId() {
        return id;
    }

    public TagCategory getCategory() {
        return category;
    }

    public String getValue() {
        return value;
    }

    public String getValueEn() {
        return valueEn;
    }

    public String getAliases() {
        return aliases;
    }

    public List<String> getAliasList() {
        return aliases == null || aliases.isBlank() ? List.of()
                : Arrays.stream(aliases.split(",")).map(String::trim).filter(a -> !a.isEmpty()).toList();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setValueEn(String valueEn) {
        this.valueEn = valueEn;
    }

    public void setAliases(String aliases) {
        this.aliases = aliases;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public String getDeletedInfo() {
        return deletedInfo;
    }

    /**
     * Мягкое удаление: тег скрывается и отвязывается от материалов, SKU и кейсов. Название и ключ меняются, чтобы
     * освободить имя для нового тега (значение и ключ уникальны); исходные данные сохраняются в deletedInfo.
     */
    public void markDeleted(String info) {
        this.deleted = true;
        this.active = false;
        this.deletedInfo = info;
        this.value = this.value + " [удален #" + this.id + "]";
        this.normKey = "deleted:" + this.id;
        this.valueEn = null;
        this.aliases = null;
    }
}
