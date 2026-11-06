package com.example.order.service;

import com.example.order.exception.OrderExceptions.IdempotencyKeyMismatchException;
import com.example.order.exception.OrderExceptions.IdempotencyRequestInProgressException;
import com.example.order.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a real H2 database. The test-managed transaction is switched off so that each
 * service call commits on its own, exactly as in production.
 */
@DataJpaTest(properties = "eureka.client.enabled=false")
@Import(IdempotencyService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdempotencyServiceTest {

    @Autowired
    private IdempotencyService service;

    @Autowired
    private IdempotencyRecordRepository repository;

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    @Test
    void newKey_startsProcessing() {
        String key = newKey();

        assertThat(service.start(key, "hash-a")).isEmpty();
        assertThat(repository.findById(key)).isPresent();
    }

    @Test
    void completedKey_withSamePayload_isReplayed() {
        String key = newKey();
        service.start(key, "hash-a");
        service.complete(key, 42L);

        assertThat(service.start(key, "hash-a")).contains(42L);
    }

    @Test
    void sameKey_withDifferentPayload_isRejected() {
        String key = newKey();
        service.start(key, "hash-a");
        service.complete(key, 42L);

        assertThatThrownBy(() -> service.start(key, "hash-b")).isInstanceOf(IdempotencyKeyMismatchException.class);
    }

    @Test
    void keyStillInProgress_isRejected() {
        String key = newKey();
        service.start(key, "hash-a");

        assertThatThrownBy(() -> service.start(key, "hash-a")).isInstanceOf(IdempotencyRequestInProgressException.class);
    }

    @Test
    void releasedKey_canBeUsedAgain() {
        String key = newKey();
        service.start(key, "hash-a");
        service.release(key);

        assertThat(service.start(key, "hash-a")).isEmpty();
    }

    @Test
    void hash_isStableAndHex() {
        assertThat(IdempotencyService.hash("productId=1;quantity=2"))
                .isEqualTo(IdempotencyService.hash("productId=1;quantity=2"))
                .hasSize(64)
                .matches("[0-9a-f]+");
    }
}
