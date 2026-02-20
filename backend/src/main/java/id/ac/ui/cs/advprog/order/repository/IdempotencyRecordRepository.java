package id.ac.ui.cs.advprog.order.repository;

import id.ac.ui.cs.advprog.order.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByIdemKey(String idemKey);
}
