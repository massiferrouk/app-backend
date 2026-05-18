package com.yuniv.backend.exception;

import com.yuniv.backend.security.CustomUserDetailsService;
import com.yuniv.backend.security.JwtUtil;
import com.yuniv.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
    private AuthService authService;

    @MockitoBean
    private JwtUtil jwtUtil;

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

        @GetMapping("/test/payment")
        public void payment() {
            throw new PaymentException("Paiement refusé par Stripe");
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
    void shouldReturn402OnPaymentError() throws Exception {
        mockMvc.perform(get("/test/payment").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_ERROR"))
                .andExpect(jsonPath("$.message").value("Paiement refusé par Stripe"));
    }

    @Test
    void shouldReturn500OnUnexpectedError() throws Exception {
        mockMvc.perform(get("/test/generic-error").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Une erreur inattendue s'est produite"));
    }
}
