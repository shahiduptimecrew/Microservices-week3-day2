package com.example.order.service;

import com.example.order.entity.IdempotencyRecord;
import com.example.order.exception.OrderExceptions.IdempotencyKeyMismatchException;
import com.example.order.exception.OrderExceptions.IdempotencyRequestInProgressException;
import com.example.order.repository.IdempotencyRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Database-backed {@code Idempotency-Key} handling for {@code POST /api/v1/orders}.
 * <pre>
 * key unknown                      → insert IN_PROGRESS row, caller processes the request
 * key known, COMPLETED, same body  → replay: return the order created the first time (no new order)
 * key known, different body        → 409 (client bug: one key, two different requests)
 * key known, IN_PROGRESS           → 409 (the first request is still running)
 * </pre>
 * Each method runs in its own short transaction, so the IN_PROGRESS row is committed (and
 * visible to concurrent requests) <b>before</b> the slow remote calls start.
 */
@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;

    public IdempotencyService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * @return the id of the order created earlier with this key (replay), or empty if the caller
     *         should process the request now
     */
    public Optional<Long> start(String key, String requestHash) {
        Optional<IdempotencyRecord> existing = repository.findById(key);
        if (existing.isPresent()) {
            return Optional.of(replayOrReject(existing.get(), key, requestHash));
        }
        try {
            repository.saveAndFlush(new IdempotencyRecord(key, requestHash));
            return Optional.empty();
        } catch (DataIntegrityViolationException e) {
            // Another request with the same key inserted its row between our read and our insert.
            IdempotencyRecord winner = repository.findById(key)
                    .orElseThrow(() -> new IdempotencyRequestInProgressException(key));
            return Optional.of(replayOrReject(winner, key, requestHash));
        }
    }

    @Transactional
    public void complete(String key, Long orderId) {
        repository.findById(key).ifPresent(record -> record.complete(orderId));
    }

    /** The request failed before an order existed: free the key so the client can retry with it. */
    @Transactional
    public void release(String key) {
        repository.deleteById(key);
    }

    public static String hash(String canonicalPayload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static Long replayOrReject(IdempotencyRecord record, String key, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyMismatchException(key);
        }
        if (record.getStatus() == IdempotencyRecord.Status.IN_PROGRESS) {
            throw new IdempotencyRequestInProgressException(key);
        }
        return record.getOrderId();
    }
}
