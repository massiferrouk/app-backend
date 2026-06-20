package com.studup.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileValidationServiceTest {

    private final FileValidationService fileValidationService = new FileValidationService();

    // ─── validateImage ────────────────────────────────────────────────────────

    @Test
    void shouldAcceptValidJpeg() {
        // Magic bytes JPEG : FF D8 FF E0
        byte[] jpegBytes = new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", jpegBytes
        );

        assertThatCode(() -> fileValidationService.validateImage(file))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptValidPng() {
        // Magic bytes PNG : 89 50 4E 47 0D 0A 1A 0A
        byte[] pngBytes = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", pngBytes
        );

        assertThatCode(() -> fileValidationService.validateImage(file))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectExecutableRenamedAsJpeg() {
        // Magic bytes EXE : 4D 5A (MZ)
        // Même si l'extension est .jpg, Tika détecte que c'est un exécutable
        byte[] exeBytes = new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file", "virus.jpg", "image/jpeg", exeBytes
        );

        assertThatThrownBy(() -> fileValidationService.validateImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Format non supporté");
    }

    @Test
    void shouldRejectPdfAsImage() {
        // Magic bytes PDF : 25 50 44 46 (%PDF)
        byte[] pdfBytes = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31};
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", pdfBytes
        );

        assertThatThrownBy(() -> fileValidationService.validateImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Format non supporté");
    }

    @Test
    void shouldRejectOversizedImage() {
        // Fichier de 6 Mo — dépasse la limite de 5 Mo
        byte[] bigContent = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", bigContent
        );

        assertThatThrownBy(() -> fileValidationService.validateImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 Mo");
    }

    // ─── validateDocument ─────────────────────────────────────────────────────

    @Test
    void shouldAcceptValidPdf() {
        // Magic bytes PDF : 25 50 44 46 (%PDF)
        byte[] pdfBytes = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31};
        MockMultipartFile file = new MockMultipartFile(
                "file", "contrat.pdf", "application/pdf", pdfBytes
        );

        assertThatCode(() -> fileValidationService.validateDocument(file))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectExecutableRenamedAsPdf() {
        // Magic bytes EXE renommé en .pdf — Tika détecte le vrai type
        byte[] exeBytes = new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file", "virus.pdf", "application/pdf", exeBytes
        );

        assertThatThrownBy(() -> fileValidationService.validateDocument(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Format non supporté");
    }

    @Test
    void shouldRejectOversizedDocument() {
        // Fichier de 11 Mo — dépasse la limite de 10 Mo
        byte[] bigContent = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "contrat.pdf", "application/pdf", bigContent
        );

        assertThatThrownBy(() -> fileValidationService.validateDocument(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 Mo");
    }
}
