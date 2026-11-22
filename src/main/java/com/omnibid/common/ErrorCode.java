package com.omnibid.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 Bad Request
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ"),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "Tệp ảnh không hợp lệ"),

    // 401 Unauthorized
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Chưa xác thực hoặc phiên đăng nhập không hợp lệ"),

    // 403 Forbidden
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện hành động này"),
    ACCOUNT_BANNED(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa"),

    // 404 Not Found
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),

    // 409 Conflict
    AUCTION_NOT_ACTIVE(HttpStatus.CONFLICT, "Phiên đấu giá không đang diễn ra"),
    BID_TOO_LOW(HttpStatus.CONFLICT, "Mức giá đặt thấp hơn mức tối thiểu"),
    ALREADY_LEADING(HttpStatus.CONFLICT, "Bạn đang là người dẫn đầu phiên đấu giá này"),
    SELF_BID_NOT_ALLOWED(HttpStatus.CONFLICT, "Không thể tự đặt giá trên phiên của chính mình"),
    AUCTION_NOT_EDITABLE(HttpStatus.CONFLICT, "Phiên đấu giá không thể chỉnh sửa ở trạng thái hiện tại"),
    CANNOT_CANCEL(HttpStatus.CONFLICT, "Không thể hủy phiên đấu giá này"),
    PRODUCT_NOT_AVAILABLE(HttpStatus.CONFLICT, "Sản phẩm không khả dụng để tạo phiên đấu giá"),
    PRODUCT_NOT_EDITABLE(HttpStatus.CONFLICT, "Sản phẩm không thể chỉnh sửa ở trạng thái hiện tại"),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "Chuyển trạng thái không hợp lệ"),
    CANNOT_BAN_ADMIN(HttpStatus.CONFLICT, "Không thể khóa tài khoản quản trị viên"),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "Danh mục đang có sản phẩm, không thể xóa"),
    ORDER_EXPIRED(HttpStatus.CONFLICT, "Đơn hàng đã quá hạn thanh toán"),
    ORDER_CANCELLED(HttpStatus.CONFLICT, "Đơn hàng đã bị hủy"),
    CONFLICT(HttpStatus.CONFLICT, "Xung đột dữ liệu, vui lòng thử lại"),

    // 413 Payload Too Large
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Dung lượng yêu cầu vượt quá giới hạn cho phép (25MB)"),

    // 429 Too Many Requests
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Quá nhiều yêu cầu, vui lòng thử lại sau"),

    // 500 Internal Server Error
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi hệ thống"),

    // 502 Bad Gateway
    IMAGE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "Không thể tải ảnh lên dịch vụ lưu trữ");

    private final HttpStatus status;
    private final String defaultMessage;
}
