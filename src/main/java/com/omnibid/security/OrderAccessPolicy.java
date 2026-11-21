package com.omnibid.security;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.entity.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OrderAccessPolicy {

    public void requireViewer(Order o, CurrentUser u) {
        if (!(u.isAdmin() || u.id().equals(o.getBuyerId()) || u.id().equals(o.getSellerId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot access this order");
        }
    }

    public void requireBuyer(Order o, CurrentUser u) {
        if (!u.id().equals(o.getBuyerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the buyer can do this");
        }
    }

    public void requireSeller(Order o, CurrentUser u) {
        if (!u.id().equals(o.getSellerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the seller can do this");
        }
    }

    public void requireSellerOrAdmin(Order o, CurrentUser u) {
        if (!(u.isAdmin() || u.id().equals(o.getSellerId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the seller or an admin can do this");
        }
    }
}
