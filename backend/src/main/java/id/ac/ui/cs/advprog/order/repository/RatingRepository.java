package id.ac.ui.cs.advprog.order.repository;

import id.ac.ui.cs.advprog.order.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    Optional<Rating> findByOrderId(Long orderId);
}