package id.ac.ui.cs.advprog.order.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "order_audit_logs", indexes = {
        @Index(name = "idx_audit_order_id", columnList = "order_id"),
        @Index(name = "idx_audit_actor_id", columnList = "actor_id")
})
public class OrderAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false, length = 30)
    private String event;

    @Column(length = 500)
    private String detail;

    @Column(nullable = false)
    private Instant createdAt;

    public OrderAuditLog() {}

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}