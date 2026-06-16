package com.studup.backend.config;

import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.service.AlternantProfileService;
import com.studup.backend.service.AuthService;
import com.studup.backend.service.LogementService;
import com.studup.backend.service.ProprietaireProfileService;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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
    private AuthService authService;

    @MockitoBean
    private AlternantProfileService alternantProfileService;

    @MockitoBean
    private ProprietaireProfileService proprietaireProfileService;

    @MockitoBean
    private LogementService logementService;

    @MockitoBean
    private JwtUtil jwtUtil;

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
