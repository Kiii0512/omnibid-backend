package com.omnibid.security;

import com.omnibid.common.AppException;
import com.omnibid.entity.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OrderAccessPolicy {

    public void requireViewer(Order o, CurrentUser u) {
        if (!(u.isAdmin() || u.id().equals(o.getBuyerId()) || u.id().equals(o.getSellerId()))) {
            throw new AppException("FORBIDDEN", "You cannot access this order", HttpStatus.FORBIDDEN);
        }
    }

    public void requireBuyer(Order o, CurrentUser u) {
        if (!u.id().equals(o.getBuyerId())) {
            throw new AppException("FORBIDDEN", "Only the buyer can do this", HttpStatus.FORBIDDEN);
        }
    }

    public void requireSellerOrAdmin(Order o, CurrentUser u) {
        if (!(u.isAdmin() || u.id().equals(o.getSellerId()))) {
            throw new AppException("FORBIDDEN", "Only the seller or an admin can do this", HttpStatus.FORBIDDEN);
        }
    }
}
