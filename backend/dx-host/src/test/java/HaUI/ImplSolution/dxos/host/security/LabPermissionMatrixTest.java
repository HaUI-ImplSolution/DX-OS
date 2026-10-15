// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import HaUI.ImplSolution.dxos.core.exception.GlobalExceptionHandler;
import HaUI.ImplSolution.dxos.core.identity.CurrentUserService;
import HaUI.ImplSolution.dxos.core.security.AppRoles;
import HaUI.ImplSolution.dxos.core.security.RestAccessDeniedHandler;
import HaUI.ImplSolution.dxos.core.security.RestAuthenticationEntryPoint;
import HaUI.ImplSolution.dxos.core.security.SecurityConfig;
import HaUI.ImplSolution.dxos.core.security.ServiceApiKeyAuthenticationFilter;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceDirectory;
import HaUI.ImplSolution.dxos.lab.LabSecurityPolicies;
import HaUI.ImplSolution.dxos.lab.controller.RoomBookingController;
import HaUI.ImplSolution.dxos.lab.dto.BookingDtos.BookingResponse;
import HaUI.ImplSolution.dxos.lab.service.RoomBookingService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

// Who may book a room and who may decide on a booking, checked against the real security
// configuration with the service mocked out.
@WebMvcTest(controllers = RoomBookingController.class,
    properties = "ai.service.inbound-api-key=test-key")
@Import({SecurityConfig.class, LabSecurityPolicies.class, CurrentUserService.class, AppRoles.class,
    RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
    ServiceApiKeyAuthenticationFilter.class, GlobalExceptionHandler.class,
    RoomBookingController.class})
class LabPermissionMatrixTest {

  // The controllers refer to this bean by name in their security expressions.
  @TestConfiguration
  static class CurrentUserBeans {
    @Bean
    CurrentUserService currentUserService(WorkspaceDirectory workspaceDirectory,
        AppRoles appRoles) {
      return new CurrentUserService(workspaceDirectory, appRoles);
    }
  }

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private RoomBookingService bookingService;
  @MockitoBean
  private WorkspaceDirectory workspaceDirectory;

  private static JwtRequestPostProcessor as(String role) {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))
        .jwt(jwt -> jwt.subject("user-1").claim("azp", "dxos-web"));
  }

  private static RequestBuilder create(JwtRequestPostProcessor user) {
    return post("/api/v1/room-bookings").with(user).contentType(MediaType.APPLICATION_JSON)
        .content("{\"roomId\":1,\"title\":\"Thực hành\",\"attendees\":10,"
            + "\"startsAt\":\"2030-01-01T01:00:00Z\",\"endsAt\":\"2030-01-01T03:00:00Z\"}");
  }

  @BeforeEach
  void servicesAnswer() {
    BookingResponse booking = new BookingResponse(1L, 1L, "A2-301", "user-1", "User", "LECTURER",
        "Thực hành", 10, Instant.parse("2030-01-01T01:00:00Z"),
        Instant.parse("2030-01-01T03:00:00Z"), "PENDING", null, null);
    when(bookingService.create(any(), any())).thenReturn(booking);
    when(bookingService.approve(any())).thenReturn(booking);
    when(bookingService.reject(any(), any())).thenReturn(booking);
    when(bookingService.cancel(any())).thenReturn(booking);
  }

  @Test
  void studentCannotBookARoom() throws Exception {
    mockMvc.perform(create(as("STUDENT"))).andExpect(status().isForbidden());
  }

  @Test
  void lecturerTechnicianAndAdminCanBookARoom() throws Exception {
    for (String role : new String[] {"LECTURER", "TECHNICIAN", "ADMIN"}) {
      mockMvc.perform(create(as(role))).andExpect(status().isCreated());
    }
  }

  @Test
  void onlyTechnicianAndAdminDecideOnBookings() throws Exception {
    for (String role : new String[] {"STUDENT", "LECTURER"}) {
      mockMvc.perform(post("/api/v1/room-bookings/1/approve").with(as(role)))
          .andExpect(status().isForbidden());
      mockMvc.perform(post("/api/v1/room-bookings/1/reject").with(as(role)))
          .andExpect(status().isForbidden());
    }
    for (String role : new String[] {"TECHNICIAN", "ADMIN"}) {
      mockMvc.perform(post("/api/v1/room-bookings/1/approve").with(as(role)))
          .andExpect(status().isOk());
      mockMvc.perform(post("/api/v1/room-bookings/1/reject").with(as(role))
          .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Phòng dành cho thi\"}"))
          .andExpect(status().isOk());
    }
  }

  @Test
  void anonymousCallerIsUnauthorized() throws Exception {
    mockMvc.perform(post("/api/v1/room-bookings/1/cancel")).andExpect(status().isUnauthorized());
  }
}
