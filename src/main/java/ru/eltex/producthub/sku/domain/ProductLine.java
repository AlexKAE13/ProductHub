package ru.eltex.producthub.sku.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import ru.eltex.producthub.security.User;

@Entity
@Table(name = "product_line")
public class ProductLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String family;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ProductLine() {
    }

    public ProductLine(String code, String name, String family, User owner, int sortOrder) {
        this.code = code;
        this.name = name;
        this.family = family;
        this.owner = owner;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getFamily() {
        return family;
    }

    public User getOwner() {
        return owner;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
