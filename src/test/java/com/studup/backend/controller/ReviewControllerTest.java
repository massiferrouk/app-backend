package com.studup.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.request.ReviewRequest;
import com.studup.backend.model.dto.response.ReviewResponse;
import com.studup.backend.model.enums.ReviewTargetType;
import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtBlacklistService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ReviewController.class)
class ReviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ReviewService reviewService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private ReviewResponse buildResponse() {
        return new ReviewResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, UUID.randomUUID(), ReviewTargetType.USER,
                5, "Excellent échange", false, OffsetDateTime.now());
    }

    // ─── POST /reviews ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn201WhenReviewCreated() throws Exception {
        ReviewRequest request = new ReviewRequest(
                UUID.randomUUID(), ReviewTargetType.USER, UUID.randomUUID(), null, 5, "Super");

        when(reviewService.createReview(eq("alice@studup.fr"), any())).thenReturn(buildResponse());

        mockMvc.perform(post("/api/v1/reviews")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.isModerated").value(false));
    }

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn400WhenRatingInvalid() throws Exception {
        ReviewRequest request = new ReviewRequest(
                UUID.randomUUID(), ReviewTargetType.USER, UUID.randomUUID(), null, 6, null);

        mockMvc.perform(post("/api/v1/reviews")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/reviews")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "charlie@studup.fr")
    void shouldReturn403WhenNotParticipant() throws Exception {
        ReviewRequest request = new ReviewRequest(
                UUID.randomUUID(), ReviewTargetType.USER, UUID.randomUUID(), null, 4, null);

        when(reviewService.createReview(any(), any()))
                .thenThrow(new UnauthorizedException("Non participant"));

        mockMvc.perform(post("/api/v1/reviews")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ─── GET /reviews/user/{userId} ───────────────────────────────────────────

    @Test
    @WithMockUser
    void shouldReturn200WithUserReviews() throws Exception {
        UUID userId = UUID.randomUUID();
        when(reviewService.getReviewsForUser(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(buildResponse())));

        mockMvc.perform(get("/api/v1/reviews/user/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].targetType").value("USER"));
    }

    // ─── POST /reviews/{id}/report ────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn204WhenReviewReported() throws Exception {
        UUID reviewId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/reviews/" + reviewId + "/report")
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }
}
