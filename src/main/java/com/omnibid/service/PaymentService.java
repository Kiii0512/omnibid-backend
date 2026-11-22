package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.payment.PaymentHistoryResponse;
import com.omnibid.dto.payment.PaymentRequest;
import com.omnibid.dto.payment.PaymentResultResponse;
import com.omnibid.security.CurrentUser;
import org.springframework.data.domain.Pageable;

public interface PaymentService {

    PaymentResultResponse pay(CurrentUser currentUser, Long orderId, String idempotencyKey, PaymentRequest request);

    PageResponse<PaymentHistoryResponse> getPaymentsByOrder(CurrentUser currentUser, Long orderId, Pageable pageable);
}
