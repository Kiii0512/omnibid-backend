package com.omnibid.security;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.entity.Order;
import org.springframework.stereotype.Component;

@Component
public class OrderAccessPolicy {

    public void requireViewer(Order o, CurrentUser u) {
        if (u.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Admin phải sử dụng API quản trị để xem đơn hàng");
        }
        if (!(u.id().equals(o.getBuyerId()) || u.id().equals(o.getSellerId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập đơn hàng này");
        }
    }

    public void requireBuyer(Order o, CurrentUser u) {
        if (u.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người mua mới được phép thực hiện");
        }
        if (!u.id().equals(o.getBuyerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người mua mới được phép thực hiện");
        }
    }

    public void requireSeller(Order o, CurrentUser u) {
        if (u.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người bán mới được phép thực hiện");
        }
        if (!u.id().equals(o.getSellerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người bán mới được phép thực hiện");
        }
    }
}
