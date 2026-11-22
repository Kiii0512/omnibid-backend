package com.omnibid.security;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.entity.Order;
import org.springframework.stereotype.Component;

@Component
public class OrderAccessPolicy {

    public void requireViewer(Order order, CurrentUser user) {
        if (user == null || user.isAdmin()
                || !(user.id().equals(order.getBuyerId()) || user.id().equals(order.getSellerId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem đơn hàng này");
        }
    }

    public void requireBuyer(Order order, CurrentUser user) {
        if (user == null || user.isAdmin() || !user.id().equals(order.getBuyerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người mua mới được phép thực hiện thao tác này");
        }
    }

    public void requireSeller(Order order, CurrentUser user) {
        if (user == null || user.isAdmin() || !user.id().equals(order.getSellerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người bán mới được phép thực hiện thao tác này");
        }
    }
}
