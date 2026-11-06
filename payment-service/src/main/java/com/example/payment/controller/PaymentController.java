package com.example.payment.controller;

import com.example.payment.dto.request.FailureModeRequest;
import com.example.payment.dto.request.PaymentRequest;
import com.example.payment.dto.response.FailureModeResponse;
import com.example.payment.dto.response.PaymentResponse;
import com.example.payment.service.PaymentProviderSimulator;
import com.example.payment.service.PaymentService;
import com.example.payment.service.PaymentService.ProcessResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * <pre>
 * POST /api/v1/payments                       201 Created (new) | 200 OK (duplicate operationId)
 * GET  /api/v1/payments/{id}                  200 | 404
 * GET  /api/v1/payments?operationId=|orderId= 200 (list)
 * GET  /api/v1/payments/test/failure-mode     200  demo only
 * POST /api/v1/payments/test/failure-mode     200  demo only
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentProviderSimulator provider;

    public PaymentController(PaymentService paymentService, PaymentProviderSimulator provider) {
        this.paymentService = paymentService;
        this.provider = provider;
    }

    /**
     * Idempotent by {@code operationId}: a retry gets the original payment back with 200,
     * and the provider is not charged again.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> process(@Valid @RequestBody PaymentRequest request) {
        ProcessResult result = paymentService.process(request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/payments/{id}")
                .buildAndExpand(result.payment().id())
                .toUri();
        if (result.created()) {
            return ResponseEntity.created(location).body(result.payment());
        }
        return ResponseEntity.ok().location(location).body(result.payment());
    }

    @GetMapping("/{id}")
    public PaymentResponse findById(@PathVariable Long id) {
        return paymentService.findById(id);
    }

    @GetMapping
    public List<PaymentResponse> find(@RequestParam(required = false) String operationId,
                                      @RequestParam(required = false) Long orderId) {
        return paymentService.find(operationId, orderId);
    }

    /** DEMO ONLY. A real service would not expose this; it would be a test fixture or feature flag. */
    @GetMapping("/test/failure-mode")
    public FailureModeResponse getFailureMode() {
        return new FailureModeResponse(provider.getMode(), provider.getDelayMs());
    }

    /** DEMO ONLY. Example body: {@code {"mode":"ERROR"}} or {@code {"mode":"SLOW","delayMs":5000}}. */
    @PostMapping("/test/failure-mode")
    public FailureModeResponse setFailureMode(@Valid @RequestBody FailureModeRequest request) {
        provider.setMode(request.mode(), request.delayMs());
        return getFailureMode();
    }
}
