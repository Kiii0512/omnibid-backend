package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.dto.payment.PaymentHistoryResponse;
import com.omnibid.dto.payment.PaymentRequest;
import com.omnibid.dto.payment.PaymentResponse;
import com.omnibid.enums.PaymentStatus;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> pay(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long orderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        PaymentResponse response = paymentService.pay(currentUser, orderId, idempotencyKey, request);

        if (response.getStatus() == PaymentStatus.PENDING) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
        }

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentHistoryResponse>>> getPayments(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long orderId) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        List<PaymentHistoryResponse> list = paymentService.getPaymentsByOrder(currentUser, orderId);

        return ResponseEntity.ok(ApiResponse.success(list));
    }
}
