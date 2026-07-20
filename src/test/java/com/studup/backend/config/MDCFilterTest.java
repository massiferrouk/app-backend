package com.studup.backend.config;

import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtBlacklistService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.service.AlternantProfileService;
import com.studup.backend.service.AuthService;
import com.studup.backend.service.DisponibiliteService;
import com.studup.backend.service.GeocodingService;
import com.studup.backend.service.LogementService;
import com.studup.backend.service.AccordService;
import com.studup.backend.service.CandidatureService;
import com.studup.backend.service.UserService;
import com.studup.backend.service.MessageService;
import com.studup.backend.service.NotificationService;
import com.studup.backend.service.AlternantDashboardService;
import com.studup.backend.service.ICalExportService;
import com.studup.backend.service.MediaMessageService;
import com.studup.backend.service.ModerationService;
import com.studup.backend.service.ProprietaireDashboardService;
import com.studup.backend.service.ReputationService;
import com.studup.backend.service.ReviewService;
import com.studup.backend.service.AdminService;
import com.studup.backend.service.CalendrierService;
import com.studup.backend.service.MatchingService;
import com.studup.backend.service.ProprietaireProfileService;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.studup.backend.repository.UserRepository;
import com.studup.backend.service.EmailConfirmationService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({MDCFilter.class, MDCFilterTest.FakeController.class})
class MDCFilterTest {

    @Autowired
    private MockMvc mockMvc;

    // AuthController est chargé par @WebMvcTest (tous les @RestController sont inclus).
    // On fournit un mock d'AuthService pour satisfaire sa dépendance — ce test ne s'en sert pas.
    @MockitoBean
    private EmailConfirmationService emailConfirmationService;

    // Exigé par UserController (APP-78) — les @WebMvcTest larges chargent tous les controllers
    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AlternantProfileService alternantProfileService;

    @MockitoBean
    private ProprietaireProfileService proprietaireProfileService;

    @MockitoBean
    private LogementService logementService;

    @MockitoBean
    private GeocodingService geocodingService;

    @MockitoBean
    private DisponibiliteService disponibiliteService;

    @MockitoBean
    private MatchingService matchingService;

    @MockitoBean
    private CalendrierService calendrierService;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private AccordService accordService;

    // Exigé par CandidatureController (APP-117) — @WebMvcTest large
    @MockitoBean
    private CandidatureService candidatureService;

    // Exigé par UserController depuis le changement de mode (APP-117)
    @MockitoBean
    private UserService userService;

    @MockitoBean
    private MessageService messageService;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private ReputationService reputationService;

    @MockitoBean
    private ProprietaireDashboardService proprietaireDashboardService;

    @MockitoBean
    private AlternantDashboardService alternantDashboardService;

    @MockitoBean
    private ICalExportService iCalExportService;

    @MockitoBean
    private MediaMessageService mediaMessageService;

    @MockitoBean
    private ModerationService moderationService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private JwtBlacklistService jwtBlacklistService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // Capture la valeur du MDC pendant le traitement de la requête
    static String capturedRequestId;

    @RestController
    static class FakeController {

        @GetMapping("/test/mdc")
        public String mdc() {
            capturedRequestId = MDC.get("requestId");
            return "ok";
        }
    }

    @Test
    void shouldInjectRequestIdInMDC() throws Exception {
        mockMvc.perform(get("/test/mdc"))
                .andExpect(status().isOk());

        assertThat(capturedRequestId).isNotNull().isNotBlank();
    }

    @Test
    void shouldUseProvidedRequestId() throws Exception {
        String requestId = "mon-request-id-flutter";

        mockMvc.perform(get("/test/mdc").header("X-Request-ID", requestId))
                .andExpect(status().isOk());

        assertThat(capturedRequestId).isEqualTo(requestId);
    }

    @Test
    void shouldReturnRequestIdInResponseHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/mdc"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Request-ID")).isNotBlank();
    }

    @Test
    void shouldClearMDCAfterRequest() throws Exception {
        mockMvc.perform(get("/test/mdc"))
                .andExpect(status().isOk());

        // Après la requête, le MDC doit être vide
        assertThat(MDC.get("requestId")).isNull();
    }
}
