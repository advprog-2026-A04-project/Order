package id.ac.ui.cs.advprog.order.repository;

import id.ac.ui.cs.advprog.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);
    List<Order> findByJastiperIdOrderByCreatedAtDesc(Long jastiperId);
}