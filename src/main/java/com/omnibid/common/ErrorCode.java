package com.omnibid.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ"),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "Hình ảnh không hợp lệ"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Yêu cầu chưa được xác thực hoặc thông tin xác thực không hợp lệ"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện hành động này"),
    ACCOUNT_BANNED(HttpStatus.FORBIDDEN, "Tài khoản của bạn đã bị khóa"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên yêu cầu"),
    AUCTION_NOT_ACTIVE(HttpStatus.CONFLICT, "Phiên đấu giá không ở trạng thái hoạt động"),
    BID_TOO_LOW(HttpStatus.CONFLICT, "Giá đặt thấp hơn mức tối thiểu yêu cầu"),
    ALREADY_LEADING(HttpStatus.CONFLICT, "Bạn đang là người đặt giá cao nhất"),
    SELF_BID_NOT_ALLOWED(HttpStatus.CONFLICT, "Người bán không được phép đặt giá trên phiên của chính mình"),
    AUCTION_NOT_EDITABLE(HttpStatus.CONFLICT, "Phiên đấu giá không thể chỉnh sửa"),
    CANNOT_CANCEL(HttpStatus.CONFLICT, "Không thể hủy phiên đấu giá"),
    PRODUCT_NOT_AVAILABLE(HttpStatus.CONFLICT, "Sản phẩm không ở trạng thái sẵn sàng"),
    PRODUCT_NOT_EDITABLE(HttpStatus.CONFLICT, "Sản phẩm không thể chỉnh sửa"),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "Chuyển đổi trạng thái không hợp lệ"),
    CANNOT_BAN_ADMIN(HttpStatus.CONFLICT, "Không thể khóa tài khoản quản trị viên"),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "Danh mục đang được sử dụng"),
    ORDER_EXPIRED(HttpStatus.CONFLICT, "Đơn hàng đã hết hạn thanh toán"),
    ORDER_CANCELLED(HttpStatus.CONFLICT, "Đơn hàng đã bị hủy"),
    CONFLICT(HttpStatus.CONFLICT, "Dữ liệu xung đột"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Dung lượng yêu cầu vượt quá giới hạn"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Quá nhiều yêu cầu không hợp lệ"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi hệ thống"),
    IMAGE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "Tải hình ảnh thất bại");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}
