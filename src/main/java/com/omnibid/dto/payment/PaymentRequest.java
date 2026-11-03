package com.omnibid.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.omnibid.enums.PaymentMethod;
import com.omnibid.enums.SimulatedOutcome;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentRequest {

    @NotNull(message = "Phương thức thanh toán không được để trống")
    private PaymentMethod method;

    private SimulatedOutcome simulateResult;
}
