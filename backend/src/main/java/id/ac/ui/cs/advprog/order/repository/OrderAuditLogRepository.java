package id.ac.ui.cs.advprog.order.repository;

import id.ac.ui.cs.advprog.order.entity.OrderAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderAuditLogRepository extends JpaRepository<OrderAuditLog, Long> {
    List<OrderAuditLog> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}