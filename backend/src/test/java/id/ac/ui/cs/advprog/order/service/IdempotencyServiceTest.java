package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.entity.IdempotencyRecord;
import id.ac.ui.cs.advprog.order.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository repository;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new IdempotencyService(repository);
    }

    @Test
    void findExistingOrderId_returnsEmpty_whenKeyNotFound() {
        when(repository.findByIdemKey("key-1")).thenReturn(Optional.empty());
        assertThat(service.findExistingOrderId("key-1")).isEmpty();
    }

    @Test
    void findExistingOrderId_returnsOrderId_whenKeyExists() {
        IdempotencyRecord rec = new IdempotencyRecord();
        rec.setIdemKey("key-2");
        rec.setOrderId(99L);
        when(repository.findByIdemKey("key-2")).thenReturn(Optional.of(rec));

        assertThat(service.findExistingOrderId("key-2")).contains(99L);
    }

    @Test
    void record_savesNewRecord_whenKeyNotExist() {
        when(repository.findByIdemKey("new-key")).thenReturn(Optional.empty());
        service.record("new-key", 1L, "checkout", "hashABC", 42L);

        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(repository).save(captor.capture());
        IdempotencyRecord saved = captor.getValue();
        assertThat(saved.getIdemKey()).isEqualTo("new-key");
        assertThat(saved.getBuyerId()).isEqualTo(1L);
        assertThat(saved.getOrderId()).isEqualTo(42L);
        assertThat(saved.getEndpoint()).isEqualTo("checkout");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void record_skips_whenKeyAlreadyExists() {
        IdempotencyRecord existing = new IdempotencyRecord();
        existing.setIdemKey("dup-key");
        when(repository.findByIdemKey("dup-key")).thenReturn(Optional.of(existing));

        service.record("dup-key", 1L, "checkout", "hash", 10L);
        verify(repository, never()).save(any());
    }

    @Test
    void record_skips_whenKeyIsNull() {
        service.record(null, 1L, "checkout", "hash", 10L);
        verify(repository, never()).findByIdemKey(any());
        verify(repository, never()).save(any());
    }

    @Test
    void record_skips_whenKeyIsBlank() {
        service.record("   ", 1L, "checkout", "hash", 10L);
        verify(repository, never()).findByIdemKey(any());
        verify(repository, never()).save(any());
    }

    @Test
    void hash_returnsDeterministicResult() {
        String h1 = IdempotencyService.hash("hello-world");
        String h2 = IdempotencyService.hash("hello-world");
        assertThat(h1).isEqualTo(h2).hasSize(64);
    }

    @Test
    void hash_returnsDifferentResultsForDifferentInputs() {
        assertThat(IdempotencyService.hash("a")).isNotEqualTo(IdempotencyService.hash("b"));
    }
}