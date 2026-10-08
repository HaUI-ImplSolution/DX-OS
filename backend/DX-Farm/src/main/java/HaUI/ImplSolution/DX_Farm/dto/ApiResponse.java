// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

// success: request có thành công hay không
// message: thông điệp mô tả kết quả
// data: dữ liệu trả về (null nếu lỗi hoặc không có)
// timestamp: thời điểm phản hồi được tạo
// errorCode: mã lỗi ổn định cho client ánh xạ sang thông báo tiếng Việt (chỉ có khi lỗi)
public record ApiResponse<T>(boolean success, String message, T data, Instant timestamp,
		@JsonInclude(JsonInclude.Include.NON_NULL) String errorCode) {

	public static <T> ApiResponse<T> success(T data) {
		return success("OK", data);
	}

	public static <T> ApiResponse<T> success(String message, T data) {
		return new ApiResponse<>(true, message, data, Instant.now(), null);
	}

	public static ApiResponse<Void> error(String errorCode, String message) {
		return new ApiResponse<>(false, message, null, Instant.now(), errorCode);
	}

	public static <T> ApiResponse<T> error(String errorCode, String message, T data) {
		return new ApiResponse<>(false, message, data, Instant.now(), errorCode);
	}
}
