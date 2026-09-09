package HaUI.ImplSolution.DX_Farm.dto;

import java.time.Instant;

// success: request có thành công hay không
// message: thông điệp mô tả kết quả
// data: dữ liệu trả về (null nếu lỗi hoặc không có)
// timestamp: thời điểm phản hồi được tạo
public record ApiResponse<T>(boolean success, String message, T data, Instant timestamp) {

	public static <T> ApiResponse<T> success(T data) {
		return success("OK", data);
	}

	public static <T> ApiResponse<T> success(String message, T data) {
		return new ApiResponse<>(true, message, data, Instant.now());
	}

	public static ApiResponse<Void> error(String message) {
		return new ApiResponse<>(false, message, null, Instant.now());
	}

	public static <T> ApiResponse<T> error(String message, T data) {
		return new ApiResponse<>(false, message, data, Instant.now());
	}
}
