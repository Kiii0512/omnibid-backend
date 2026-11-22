package com.omnibid.service;

import com.omnibid.dto.payment.PaymentHistoryResponse;
import com.omnibid.dto.payment.PaymentRequest;
import com.omnibid.dto.payment.PaymentResponse;
import com.omnibid.security.CurrentUser;

import java.util.List;

public interface PaymentService {

    PaymentResponse pay(CurrentUser currentUser, Long orderId, String idempotencyKey, PaymentRequest request);

    List<PaymentHistoryResponse> getPaymentsByOrder(CurrentUser currentUser, Long orderId);
}
