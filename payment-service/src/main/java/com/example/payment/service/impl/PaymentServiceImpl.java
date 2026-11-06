package com.example.payment.service.impl;

import com.example.payment.dto.request.PaymentRequest;
import com.example.payment.dto.response.PaymentResponse;
import com.example.payment.entity.Payment;
import com.example.payment.exception.OperationIdConflictException;
import com.example.payment.exception.PaymentNotFoundException;
import com.example.payment.mapper.PaymentMapper;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.service.PaymentProviderSimulator;
import com.example.payment.service.PaymentProviderSimulator.ChargeResult;
import com.example.payment.service.PaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Processes payments with operation-ID deduplication.
 * <p>
 * Same {@code operationId} and same payload: the stored payment is returned (200) and the
 * provider is <b>not</b> called again. Same {@code operationId} with a different order or
 * amount: 409, because that is a client bug, not a retry.
 */
@Service
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository repository;
    private final PaymentProviderSimulator provider;
    private final PaymentMapper mapper;

    public PaymentServiceImpl(PaymentRepository repository, PaymentProviderSimulator provider, PaymentMapper mapper) {
        this.repository = repository;
        this.provider = provider;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ProcessResult process(PaymentRequest request) {
        Optional<Payment> existing = repository.findByOperationId(request.operationId());
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (!sameOperation(payment, request)) {
                throw new OperationIdConflictException(request.operationId());
            }
            return new ProcessResult(mapper.toResponse(payment), false);
        }

        ChargeResult result = provider.charge(request.operationId(), request.amount());
        Payment saved = repository.saveAndFlush(new Payment(
                request.operationId(),
                request.orderId(),
                request.amount(),
                request.currencyOrDefault(),
                result.status(),
                result.failureReason()));
        return new ProcessResult(mapper.toResponse(saved), true);
    }

    @Override
    public PaymentResponse findById(Long id) {
        return repository.findById(id).map(mapper::toResponse).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    @Override
    public List<PaymentResponse> find(String operationId, Long orderId) {
        if (operationId != null) {
            return repository.findByOperationId(operationId).map(mapper::toResponse).stream().toList();
        }
        if (orderId != null) {
            return repository.findByOrderIdOrderByIdAsc(orderId).stream().map(mapper::toResponse).toList();
        }
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    private static boolean sameOperation(Payment payment, PaymentRequest request) {
        return payment.getOrderId().equals(request.orderId())
                && payment.getAmount().compareTo(request.amount()) == 0
                && payment.getCurrency().equals(request.currencyOrDefault());
    }
}
