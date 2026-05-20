package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.entity.OrderAuditLog;
import id.ac.ui.cs.advprog.order.repository.OrderAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

class OrderAuditServiceTest {

    @Mock
    private OrderAuditLogRepository repository;

    private OrderAuditService auditService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        auditService = new OrderAuditService(repository);
    }

    @Test
    void log_persistsEntryWithAllFields() {
        auditService.log(5L, 2L, "CHECKOUT_PAID", "total=100000");

        ArgumentCaptor<OrderAuditLog> captor = ArgumentCaptor.forClass(OrderAuditLog.class);
        verify(repository).save(captor.capture());
        OrderAuditLog entry = captor.getValue();
        assertThat(entry.getOrderId()).isEqualTo(5L);
        assertThat(entry.getActorId()).isEqualTo(2L);
        assertThat(entry.getEvent()).isEqualTo("CHECKOUT_PAID");
        assertThat(entry.getDetail()).isEqualTo("total=100000");
        assertThat(entry.getCreatedAt()).isNotNull();
    }

    @Test
    void log_allowsNullActorId() {
        auditService.log(1L, null, "SYSTEM_EVENT", "automated");

        ArgumentCaptor<OrderAuditLog> captor = ArgumentCaptor.forClass(OrderAuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getActorId()).isNull();
    }
}