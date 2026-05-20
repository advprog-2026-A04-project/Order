package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.entity.OrderAuditLog;
import id.ac.ui.cs.advprog.order.repository.OrderAuditLogRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class OrderAuditService {

    private final OrderAuditLogRepository repository;

    public OrderAuditService(OrderAuditLogRepository repository) {
        this.repository = repository;
    }

    public void log(Long orderId, Long actorId, String event, String detail) {
        OrderAuditLog entry = new OrderAuditLog();
        entry.setOrderId(orderId);
        entry.setActorId(actorId);
        entry.setEvent(event);
        entry.setDetail(detail);
        entry.setCreatedAt(Instant.now());
        repository.save(entry);
    }
}