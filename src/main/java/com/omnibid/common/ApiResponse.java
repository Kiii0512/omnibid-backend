package com.omnibid.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private ApiError error;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiError(
            String code,
            String message,
            Map<String, Object> details
    ) {
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message, Map<String, Object> details) {
        return error(code.name(), message != null ? message : code.getDefaultMessage(), details);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return error(code, message, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode code) {
        return error(code, code.getDefaultMessage(), null);
    }

    public static <T> ApiResponse<T> error(String code, String message, Map<String, Object> details) {
        Map<String, Object> normalizedDetails = (details != null && !details.isEmpty()) ? details : null;
        return new ApiResponse<>(false, null, new ApiError(code, message, normalizedDetails));
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return error(code, message, null);
    }

    /**
     * Alias giữ tương thích trong quá trình chuyển đổi sang v4. Không phát sinh trường message cấp ngoài.
     */
    public static <T> ApiResponse<T> success(T data) {
        return ok(data);
    }

    /**
     * Alias giữ tương thích trong quá trình chuyển đổi sang v4. Bỏ qua message cấp ngoài theo hợp đồng v4.
     */
    public static <T> ApiResponse<T> success(String ignoredMessage, T data) {
        return ok(data);
    }
}
