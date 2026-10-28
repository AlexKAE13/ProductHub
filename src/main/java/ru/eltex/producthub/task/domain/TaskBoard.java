package ru.eltex.producthub.task.domain;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;

import ru.eltex.producthub.security.User;

/** Своя доска задач: владелец, участники, колонки с настраиваемыми названиями и цветами. */
@Entity
@Table(name = "task_board")
public class TaskBoard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private boolean deleted;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "task_board_member", joinColumns = @JoinColumn(name = "board_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> members = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "task_board_column", joinColumns = @JoinColumn(name = "board_id"))
    @MapKeyColumn(name = "status")
    @MapKeyEnumerated(EnumType.STRING)
    private Map<TaskStatus, ColumnStyle> columns = new EnumMap<>(TaskStatus.class);

    protected TaskBoard() {
    }

    public TaskBoard(String name, String description, User owner) {
        this.name = name;
        this.description = description;
        this.owner = owner;
        this.createdAt = LocalDateTime.now();
        this.members.add(owner);
    }

    public boolean hasMember(Long userId) {
        return members.stream().anyMatch(u -> u.getId().equals(userId));
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public User getOwner() {
        return owner;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void markDeleted() {
        this.deleted = true;
    }

    public Set<User> getMembers() {
        return members;
    }

    public Map<TaskStatus, ColumnStyle> getColumns() {
        return columns;
    }
}
