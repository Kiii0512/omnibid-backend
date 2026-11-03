package com.omnibid.service;

import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;

public interface OrderService {
    Order createFromAuction(Auction auction);
}
