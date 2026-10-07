package HaUI.ImplSolution.DX_Farm.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import HaUI.ImplSolution.DX_Farm.controller.CropSeasonController;
import HaUI.ImplSolution.DX_Farm.controller.DiagnosisController;
import HaUI.ImplSolution.DX_Farm.controller.DiseaseReportController;
import HaUI.ImplSolution.DX_Farm.core.identity.HouseholdController;
import HaUI.ImplSolution.DX_Farm.controller.ParcelController;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowController;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowService;
import HaUI.ImplSolution.DX_Farm.dto.CropSeasonResponse;
import HaUI.ImplSolution.DX_Farm.dto.DiseaseReportResponse;
import HaUI.ImplSolution.DX_Farm.core.identity.HouseholdCreateResponse;
import HaUI.ImplSolution.DX_Farm.core.identity.HouseholdResponse;
import HaUI.ImplSolution.DX_Farm.dto.ParcelResponse;
import HaUI.ImplSolution.DX_Farm.service.CropSeasonService;
import HaUI.ImplSolution.DX_Farm.service.DiagnosisService;
import HaUI.ImplSolution.DX_Farm.service.DiseaseReportService;
import HaUI.ImplSolution.DX_Farm.core.identity.HouseholdService;
import HaUI.ImplSolution.DX_Farm.service.ParcelService;
import HaUI.ImplSolution.DX_Farm.service.WeatherService;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

// The permission matrix from the phase 2 plan, checked against the real security configuration
// with the services mocked out: only the role/client rules are under test here.
@WebMvcTest(controllers = {ParcelController.class, CropSeasonController.class,
    DiseaseReportController.class, DiagnosisController.class, HouseholdController.class,
    WorkflowController.class}, properties = "ai.service.inbound-api-key=test-key")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
    ServiceApiKeyAuthenticationFilter.class})
class PermissionMatrixTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ParcelService parcelService;
  @MockitoBean
  private CropSeasonService cropSeasonService;
  @MockitoBean
  private DiseaseReportService diseaseReportService;
  @MockitoBean
  private DiagnosisService diagnosisService;
  @MockitoBean
  private HouseholdService householdService;
  @MockitoBean
  private WorkflowService workflowService;
  @MockitoBean
  private WeatherService weatherService;

  private static JwtRequestPostProcessor token(String role, String client) {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))
        .jwt(jwt -> jwt.subject("user-1").claim("azp", client));
  }

  private static JwtRequestPostProcessor farmer() {
    return token("FARMER", "dxfarm-mobile");
  }

  private static JwtRequestPostProcessor engineer() {
    return token("ENGINEER", "dxfarm-frontend");
  }

  private static JwtRequestPostProcessor admin() {
    return token("ADMIN", "dxfarm-frontend");
  }

  private RequestBuilder createParcel(JwtRequestPostProcessor user) {
    return post("/api/v1/parcels").with(user).contentType(MediaType.APPLICATION_JSON)
        .content("{\"householdCode\":\"H0001\",\"areaHectares\":1,\"latitude\":10.0,"
            + "\"longitude\":105.0}");
  }

  private RequestBuilder createSeason(JwtRequestPostProcessor user) {
    return post("/api/v1/parcels/TD-01/crop-seasons").with(user)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"riceVarietyCode\":\"OM5451\",\"startDate\":\"2026-10-01\"}");
  }

  private RequestBuilder submitReport(JwtRequestPostProcessor user) {
    return multipart("/api/v1/crop-seasons/1/disease-reports")
        .file(new MockMultipartFile("image", "leaf.jpg", "image/jpeg", new byte[] {1})).with(user);
  }

  private RequestBuilder createHousehold(JwtRequestPostProcessor user) {
    return post("/api/v1/households").with(user).contentType(MediaType.APPLICATION_JSON)
        .content("{\"fullName\":\"Nguyen Van A\",\"username\":\"nguyenvana\"}");
  }

  private RequestBuilder harvest(JwtRequestPostProcessor user) {
    return post("/api/v1/crop-seasons/1/harvest").with(user).contentType(MediaType.APPLICATION_JSON)
        .content("{\"yieldKg\":1000}");
  }

  private void servicesAnswer() {
    when(parcelService.create(any(), any())).thenReturn(new ParcelResponse(1L, "TD-01", "H0001",
        "A", null, null, null, null, null, false, "APPROVED", null, null, null));
    when(parcelService.listAll(anyBoolean())).thenReturn(List.of());
    when(cropSeasonService.create(any(), any(), any()))
        .thenReturn(new CropSeasonResponse(1L, null, "TD-01", "OM 5451", "OM5451", null, null, null,
            null, "ACTIVE", "APPROVED", null, null, null, null, null, null, null, null));
    when(cropSeasonService.harvest(any(), any()))
        .thenReturn(new CropSeasonResponse(1L, null, "TD-01", "OM 5451", "OM5451", null, null, null,
            null, "HARVESTED", "APPROVED", null, null, null, null, null, null, null, null));
    when(diseaseReportService.create(any(), any(), any(), any()))
        .thenReturn(new DiseaseReportResponse(1L, 1L, "x.jpg", "A", null, null, "PENDING", null,
            null, List.of(), null));
    when(householdService.listAll()).thenReturn(List.of());
    when(householdService.create(any())).thenReturn(new HouseholdCreateResponse(
        new HouseholdResponse(1L, "H0001", "A", null, null, true, null), "a", "pw"));
  }

  @Test
  void engineerCannotCreateParcelsSeasonsOrHarvest() throws Exception {
    mockMvc.perform(createParcel(engineer())).andExpect(status().isForbidden());
    mockMvc.perform(createSeason(engineer())).andExpect(status().isForbidden());
    mockMvc.perform(harvest(engineer())).andExpect(status().isForbidden());
  }

  @Test
  void farmerAndAdminCanCreateParcelsAndSeasons() throws Exception {
    servicesAnswer();
    mockMvc.perform(createParcel(farmer())).andExpect(status().isCreated());
    mockMvc.perform(createParcel(admin())).andExpect(status().isCreated());
    mockMvc.perform(createSeason(farmer())).andExpect(status().isCreated());
    mockMvc.perform(createSeason(admin())).andExpect(status().isCreated());
    mockMvc.perform(harvest(farmer())).andExpect(status().isOk());
    mockMvc.perform(harvest(admin())).andExpect(status().isOk());
  }

  @Test
  void onlyFarmersSubmitDiseaseReports() throws Exception {
    servicesAnswer();
    mockMvc.perform(submitReport(farmer())).andExpect(status().isCreated());
    mockMvc.perform(submitReport(engineer())).andExpect(status().isForbidden());
    mockMvc.perform(submitReport(admin())).andExpect(status().isForbidden());
  }

  @Test
  void farmerCannotReviewDiagnoses() throws Exception {
    mockMvc.perform(post("/api/v1/disease-reports/1/diagnosis/approve").with(farmer()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post("/api/v1/disease-reports/1/diagnosis/apply").with(farmer())
            .contentType(MediaType.APPLICATION_JSON).content("{\"doseAmount\":1}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void onlyAdminCanSoftDelete() throws Exception {
    for (String path : List.of("/api/v1/parcels/1", "/api/v1/crop-seasons/1",
        "/api/v1/disease-reports/1")) {
      mockMvc.perform(delete(path).with(farmer())).andExpect(status().isForbidden());
      mockMvc.perform(delete(path).with(engineer())).andExpect(status().isForbidden());
      mockMvc.perform(delete(path).with(admin())).andExpect(status().isOk());
    }
  }

  @Test
  void householdsAreStaffOnlyAndAdminManagesThem() throws Exception {
    servicesAnswer();
    mockMvc.perform(get("/api/v1/households").with(farmer())).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/households").with(engineer())).andExpect(status().isOk());
    mockMvc.perform(createHousehold(engineer())).andExpect(status().isForbidden());
    mockMvc.perform(createHousehold(farmer())).andExpect(status().isForbidden());
    mockMvc.perform(createHousehold(admin())).andExpect(status().isCreated());
    mockMvc
        .perform(patch("/api/v1/households/H0001/account").with(engineer())
            .contentType(MediaType.APPLICATION_JSON).content("{\"keycloakUserId\":\"abc\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void workflowCasesAreStaffOnly() throws Exception {
    mockMvc.perform(get("/api/v1/workflow-cases").with(farmer())).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/workflow-cases/1/events").with(farmer()))
        .andExpect(status().isForbidden());
    mockMvc.perform(patch("/api/v1/workflow-cases/1/assign").with(farmer()))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/workflow-cases").with(engineer())).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/workflow-cases/1/events").with(admin()))
        .andExpect(status().isOk());
  }

  @Test
  void requestsWithoutTokenAreUnauthorized() throws Exception {
    mockMvc.perform(get("/api/v1/parcels")).andExpect(status().isUnauthorized());
  }

  static Stream<String> webClients() {
    return Stream.of("dxfarm-frontend", "dxfarm-superset", "unknown");
  }

  @ParameterizedTest
  @MethodSource("webClients")
  void farmerTokenFromAWebClientIsRejected(String client) throws Exception {
    servicesAnswer();
    mockMvc.perform(get("/api/v1/parcels").with(token("FARMER", client)))
        .andExpect(status().isForbidden());
  }

  @Test
  void farmerTokenFromTheMobileClientIsAccepted() throws Exception {
    servicesAnswer();
    mockMvc.perform(get("/api/v1/parcels").with(token("FARMER", "dxfarm-mobile")))
        .andExpect(status().isOk());
  }

  @Test
  void staffTokensAreNotRestrictedByClient() throws Exception {
    servicesAnswer();
    mockMvc.perform(get("/api/v1/parcels").with(token("ENGINEER", "dxfarm-frontend")))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/parcels").with(token("ADMIN", "dxfarm-mobile")))
        .andExpect(status().isOk());
  }

  @Test
  void onlyFarmersCanPrecheckPhotos() throws Exception {
    when(diseaseReportService.precheck(any()))
        .thenReturn(new HaUI.ImplSolution.DX_Farm.dto.ImagePrecheckResponse(true, List.of(), 256,
            256, 100, 100));
    mockMvc.perform(multipart("/api/v1/disease-reports/precheck")
        .file(new MockMultipartFile("image", "leaf.png", "image/png", new byte[] {1}))
        .with(farmer())).andExpect(status().isOk());
    mockMvc.perform(multipart("/api/v1/disease-reports/precheck")
        .file(new MockMultipartFile("image", "leaf.png", "image/png", new byte[] {1}))
        .with(engineer())).andExpect(status().isForbidden());
    mockMvc.perform(multipart("/api/v1/disease-reports/precheck")
        .file(new MockMultipartFile("image", "leaf.png", "image/png", new byte[] {1}))
        .with(admin())).andExpect(status().isForbidden());
  }

  @Test
  void farmerCannotRequestRetakeSuggestOrReadDiagnosisHistory() throws Exception {
    mockMvc
        .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .patch("/api/v1/disease-reports/1/request-retake").with(farmer())
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Ảnh mờ\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post("/api/v1/disease-reports/1/diagnosis/suggest").with(farmer())
            .contentType(MediaType.APPLICATION_JSON).content("{\"symptomText\":\"Vết hình thoi\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .get("/api/v1/disease-reports/1/diagnosis/history").with(farmer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void reportLockConflictReturns409() throws Exception {
    when(cropSeasonService.getById(1L))
        .thenThrow(new jakarta.persistence.OptimisticLockException("Stale record"));
    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .get("/api/v1/crop-seasons/1").with(farmer())).andExpect(status().isConflict());
  }

}
