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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import com.studup.backend.repository.UserRepository;
import com.studup.backend.service.EmailConfirmationService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// On exclut Spring Security ici : ce test cible CORS uniquement.
// La SecurityConfig sera configurée dans APP-002 avec .cors(withDefaults()).
@WebMvcTest(excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost:3000")
class CorsConfigTest {

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

    @Test
    void shouldAllowConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")));
    }

    @Test
    void shouldRejectUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://evil.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
