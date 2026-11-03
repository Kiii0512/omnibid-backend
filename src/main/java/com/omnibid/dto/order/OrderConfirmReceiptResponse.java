package com.omnibid.dto.order;

import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderConfirmReceiptResponse {
    private Long id;
    private OrderStatus orderStatus;
    private ProductStatus productStatus;
}
