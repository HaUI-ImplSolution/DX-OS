package HaUI.ImplSolution.DX_Farm.controller;

import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import HaUI.ImplSolution.DX_Farm.dto.UserCreateRequest;
import HaUI.ImplSolution.DX_Farm.dto.UserResponse;
import HaUI.ImplSolution.DX_Farm.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "Quản lý tài khoản - chỉ ADMIN")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  @Operation(summary = "List all user accounts")
  public ApiResponse<List<UserResponse>> listAll() {
    return ApiResponse.success(userService.listAll());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a FARMER/ENGINEER/ADMIN account")
  public ApiResponse<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
    return ApiResponse.success("User created", userService.create(request));
  }
}
