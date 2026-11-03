package com.omnibid.service;

import com.omnibid.dto.shipment.ShipmentDetailResponse;
import com.omnibid.dto.shipment.ShipmentStatusResponse;
import com.omnibid.dto.shipment.ShipmentStatusUpdateRequest;
import com.omnibid.entity.Order;
import com.omnibid.entity.Shipment;
import com.omnibid.security.CurrentUser;

public interface ShipmentService {

    Shipment createForPaidOrder(Order order);

    ShipmentDetailResponse getShipmentByOrder(CurrentUser currentUser, Long orderId);

    ShipmentStatusResponse updateShipmentStatus(CurrentUser currentUser, Long shipmentId, ShipmentStatusUpdateRequest request);
}
