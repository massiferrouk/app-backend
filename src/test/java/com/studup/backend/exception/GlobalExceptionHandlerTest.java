package com.studup.backend.exception;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import com.studup.backend.repository.UserRepository;
import com.studup.backend.service.EmailConfirmationService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import(GlobalExceptionHandlerTest.FakeController.class)
class GlobalExceptionHandlerTest {

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

    // Controller interne au test : chaque endpoint lève une exception différente
    @RestController
    static class FakeController {

        @GetMapping("/test/not-found")
        public void notFound() {
            throw new ResourceNotFoundException("Logement introuvable");
        }

        @GetMapping("/test/duplicate-email")
        public void duplicateEmail() {
            throw new DuplicateEmailException("Un compte existe déjà avec cet email");
        }

        @GetMapping("/test/unauthorized")
        public void unauthorized() {
            throw new UnauthorizedException("Accès refusé");
        }

        @GetMapping("/test/generic-error")
        public void genericError() {
            throw new RuntimeException("Erreur inattendue");
        }
    }

    @Test
    void shouldReturn404OnResourceNotFound() throws Exception {
        mockMvc.perform(get("/test/not-found").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Logement introuvable"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/not-found"));
    }

    @Test
    void shouldReturn409OnDuplicateEmail() throws Exception {
        mockMvc.perform(get("/test/duplicate-email").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.message").value("Un compte existe déjà avec cet email"));
    }

    @Test
    void shouldReturn403OnUnauthorized() throws Exception {
        mockMvc.perform(get("/test/unauthorized").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Accès refusé"));
    }

    @Test
    void shouldReturn500OnUnexpectedError() throws Exception {
        mockMvc.perform(get("/test/generic-error").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Une erreur inattendue s'est produite"));
    }
}
