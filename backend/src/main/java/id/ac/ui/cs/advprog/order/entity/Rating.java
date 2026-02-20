package id.ac.ui.cs.advprog.order.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "ratings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rating_order", columnNames = {"orderId"})
})
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private int productRating;

    @Column(nullable = false)
    private int jastiperRating;

    @Column(length = 1000)
    private String comment;

    @Column(nullable = false)
    private Instant createdAt;

    public Rating() {}

    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }

    public void setProductRating(int productRating) { this.productRating = productRating; }

    public void setJastiperRating(int jastiperRating) { this.jastiperRating = jastiperRating; }

    public void setComment(String comment) { this.comment = comment; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}