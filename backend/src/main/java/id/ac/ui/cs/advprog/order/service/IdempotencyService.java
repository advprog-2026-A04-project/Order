package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.entity.IdempotencyRecord;
import id.ac.ui.cs.advprog.order.repository.IdempotencyRecordRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;

    public IdempotencyService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    public Optional<Long> findExistingOrderId(String idemKey) {
        return repository.findByIdemKey(idemKey).map(IdempotencyRecord::getOrderId);
    }

    @Transactional
    public void record(String idemKey, Long buyerId, String endpoint, String requestHash, Long orderId) {
        if (idemKey == null || idemKey.isBlank()) return;
        if (repository.findByIdemKey(idemKey).isPresent()) return;

        IdempotencyRecord rec = new IdempotencyRecord();
        rec.setIdemKey(idemKey);
        rec.setBuyerId(buyerId);
        rec.setEndpoint(endpoint);
        rec.setRequestHash(requestHash);
        rec.setOrderId(orderId);
        rec.setCreatedAt(Instant.now());
        repository.save(rec);
    }

    public static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 64);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }
}