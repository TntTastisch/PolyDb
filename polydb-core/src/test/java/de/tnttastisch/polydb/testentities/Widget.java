package de.tnttastisch.polydb.testentities;

import de.tnttastisch.polydb.core.annotations.Column;
import de.tnttastisch.polydb.core.annotations.Entity;
import de.tnttastisch.polydb.core.annotations.Id;
import de.tnttastisch.polydb.core.annotations.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Relation-free test fixture with a broad scalar field set (string, int, boolean, enum, decimal,
 * instant). It is the workhorse entity for the repository CRUD tests and, later, for derived query
 * methods, paging and sorting, since it exposes fields of every category those features need to
 * exercise without dragging relations into the picture.
 */
@Entity
@Table(name = "widgets")
public class Widget {

    public enum Status { NEW, ACTIVE, RETIRED }

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "status", nullable = false)
    private Status status = Status.NEW;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "created_at")
    private Instant createdAt;

    public Widget() {
    }

    public Widget(UUID id, String name, int quantity) {
        this.id = id;
        this.name = name;
        this.quantity = quantity;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
