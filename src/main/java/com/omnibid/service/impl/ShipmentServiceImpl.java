package com.omnibid.service.impl;

import com.omnibid.common.AppException;
import com.omnibid.entity.Order;
import com.omnibid.entity.Shipment;
import com.omnibid.entity.ShipmentEvent;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ShipmentStatus;
import com.omnibid.repository.ShipmentEventRepository;
import com.omnibid.repository.ShipmentRepository;
import com.omnibid.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentServiceImpl implements ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentEventRepository shipmentEventRepository;

    @Override
    @Transactional
    public Shipment createForPaidOrder(Order order) {
        if (order.getStatus() != OrderStatus.PAID) {
            throw new AppException("INVALID_STATUS_TRANSITION", "Đơn hàng chưa ở trạng thái PAID", HttpStatus.CONFLICT);
        }

        Optional<Shipment> existing = shipmentRepository.findByOrderId(order.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        String trackingCode = String.format("SIM-%06d", order.getId());
        Shipment shipment = Shipment.builder()
                .orderId(order.getId())
                .trackingCode(trackingCode)
                .carrierName("SIMULATED_CARRIER")
                .status(ShipmentStatus.CREATED)
                .build();
        shipment = shipmentRepository.save(shipment);

        ShipmentEvent event = ShipmentEvent.builder()
                .shipmentId(shipment.getId())
                .status(ShipmentStatus.CREATED)
                .note("Vận đơn mô phỏng được tạo")
                .updatedBy(null)
                .build();
        shipmentEventRepository.save(event);

        return shipment;
    }
}
