package com.omnibid.service;

import com.omnibid.entity.Order;
import com.omnibid.entity.Shipment;

public interface ShipmentService {
    Shipment createForPaidOrder(Order order);
}
