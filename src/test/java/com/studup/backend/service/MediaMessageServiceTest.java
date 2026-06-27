package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.MessagePhotoResponse;
import com.studup.backend.model.entity.Message;
import com.studup.backend.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaMessageServiceTest {

    @Mock private MessageRepository messageRepository;
    @Mock private MinioService minioService;

    @InjectMocks private MediaMessageService service;

    private UUID messageId;
    private Message message;

    @BeforeEach
    void setUp() {
        messageId = UUID.randomUUID();
        message = Message.builder()
                .id(messageId)
                .conversationId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .content("Bonjour !")
                .isRead(false)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    // Image JPEG valide 1x1 pixel (encodage JPEG minimal pour les tests)
    private MockMultipartFile validJpeg(String name) {
        // Bytes d'un JPEG 1x1 pixel blanc — suffisant pour Thumbnailator
        byte[] jpegBytes = new byte[]{
            (byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,0x00,0x10,0x4A,0x46,
            0x49,0x46,0x00,0x01,0x01,0x00,0x00,0x01,0x00,0x01,0x00,0x00,
            (byte)0xFF,(byte)0xDB,0x00,0x43,0x00,0x08,0x06,0x06,0x07,0x06,
            0x05,0x08,0x07,0x07,0x07,0x09,0x09,0x08,0x0A,0x0C,0x14,0x0D,
            0x0C,0x0B,0x0B,0x0C,0x19,0x12,0x13,0x0F,0x14,0x1D,0x1A,(byte)0x1F,
            0x1E,0x1D,0x1A,0x1C,0x1C,0x20,0x24,0x2E,0x27,0x20,0x22,0x2C,
            0x23,0x1C,0x1C,0x28,0x37,0x29,0x2C,0x30,0x31,0x34,0x34,0x34,
            0x1F,0x27,0x39,0x3D,0x38,0x32,0x3C,0x2E,0x33,0x34,0x32,
            (byte)0xFF,(byte)0xC0,0x00,0x0B,0x08,0x00,0x01,0x00,0x01,0x01,0x01,0x11,0x00,
            (byte)0xFF,(byte)0xC4,0x00,0x1F,0x00,0x00,0x01,0x05,0x01,0x01,0x01,0x01,0x01,
            0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01,0x02,0x03,
            0x04,0x05,0x06,0x07,0x08,0x09,0x0A,0x0B,
            (byte)0xFF,(byte)0xC4,0x00,(byte)0xB5,0x10,0x00,0x02,0x01,0x03,0x03,0x02,
            0x04,0x03,0x05,0x05,0x04,0x04,0x00,0x00,0x01,0x7D,0x01,0x02,
            0x03,0x00,0x04,0x11,0x05,0x12,0x21,0x31,0x41,0x06,0x13,0x51,
            0x61,0x07,0x22,0x71,0x14,0x32,(byte)0x81,(byte)0x91,(byte)0xA1,0x08,
            0x23,0x42,(byte)0xB1,(byte)0xC1,0x15,0x52,(byte)0xD1,(byte)0xF0,0x24,0x33,
            0x62,0x72,(byte)0x82,0x09,0x0A,0x16,0x17,0x18,0x19,0x1A,0x25,
            0x26,0x27,0x28,0x29,0x2A,0x34,0x35,0x36,0x37,0x38,0x39,0x3A,
            0x43,0x44,0x45,0x46,0x47,0x48,0x49,0x4A,0x53,0x54,0x55,0x56,
            0x57,0x58,0x59,0x5A,0x63,0x64,0x65,0x66,0x67,0x68,0x69,0x6A,
            0x73,0x74,0x75,0x76,0x77,0x78,0x79,0x7A,(byte)0x83,(byte)0x84,(byte)0x85,
            (byte)0x86,(byte)0x87,(byte)0x88,(byte)0x89,(byte)0x8A,(byte)0x92,(byte)0x93,(byte)0x94,
            (byte)0x95,(byte)0x96,(byte)0x97,(byte)0x98,(byte)0x99,(byte)0x9A,(byte)0xA2,(byte)0xA3,
            (byte)0xA4,(byte)0xA5,(byte)0xA6,(byte)0xA7,(byte)0xA8,(byte)0xA9,(byte)0xAA,(byte)0xB2,
            (byte)0xB3,(byte)0xB4,(byte)0xB5,(byte)0xB6,(byte)0xB7,(byte)0xB8,(byte)0xB9,(byte)0xBA,
            (byte)0xC2,(byte)0xC3,(byte)0xC4,(byte)0xC5,(byte)0xC6,(byte)0xC7,(byte)0xC8,(byte)0xC9,
            (byte)0xCA,(byte)0xD2,(byte)0xD3,(byte)0xD4,(byte)0xD5,(byte)0xD6,(byte)0xD7,(byte)0xD8,
            (byte)0xD9,(byte)0xDA,(byte)0xE1,(byte)0xE2,(byte)0xE3,(byte)0xE4,(byte)0xE5,(byte)0xE6,
            (byte)0xE7,(byte)0xE8,(byte)0xE9,(byte)0xEA,(byte)0xF1,(byte)0xF2,(byte)0xF3,(byte)0xF4,
            (byte)0xF5,(byte)0xF6,(byte)0xF7,(byte)0xF8,(byte)0xF9,(byte)0xFA,
            (byte)0xFF,(byte)0xDA,0x00,0x08,0x01,0x01,0x00,0x00,0x3F,0x00,
            (byte)0xFB,0x00,(byte)0xFF,(byte)0xD9
        };
        return new MockMultipartFile(name, name + ".jpg", "image/jpeg", jpegBytes);
    }

    // ─── upload photos valides → URLs signées retournées ─────────────────────

    @Test
    void shouldUploadPhotosAndReturnSignedUrls() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(minioService.generatePresignedUrl(anyString(), eq(24), eq(TimeUnit.HOURS)))
                .thenReturn("https://minio/signed-url-1")
                .thenReturn("https://minio/signed-url-2");

        List<MultipartFile> files = List.of(validJpeg("photo1"), validJpeg("photo2"));

        MessagePhotoResponse response = service.uploadPhotos(messageId, files);

        assertThat(response.messageId()).isEqualTo(messageId.toString());
        assertThat(response.photoUrls()).hasSize(2);
        assertThat(response.photoUrls()).containsExactly(
                "https://minio/signed-url-1", "https://minio/signed-url-2");

        verify(minioService, times(2)).uploadFile(
                anyString(), any(), eq("image/jpeg"), anyLong());
    }

    // ─── message introuvable → ResourceNotFoundException ─────────────────────

    @Test
    void shouldThrowWhenMessageNotFound() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.empty());

        List<MultipartFile> files = List.of(validJpeg("photo1"));

        assertThatThrownBy(() -> service.uploadPhotos(messageId, files))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Message introuvable");
    }

    // ─── plus de 5 photos → IllegalArgumentException ─────────────────────────

    @Test
    void shouldRejectMoreThanFivePhotos() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        List<MultipartFile> files = List.of(
                validJpeg("p1"), validJpeg("p2"), validJpeg("p3"),
                validJpeg("p4"), validJpeg("p5"), validJpeg("p6"));

        assertThatThrownBy(() -> service.uploadPhotos(messageId, files))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Maximum 5 photos");
    }

    // ─── type MIME non autorisé → IllegalArgumentException ───────────────────

    @Test
    void shouldRejectNonImageFile() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        MockMultipartFile pdf = new MockMultipartFile(
                "photo", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.uploadPhotos(messageId, List.of(pdf)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type de fichier non autorisé");
    }

    // ─── fichier trop volumineux → IllegalArgumentException ──────────────────

    @Test
    void shouldRejectOversizedFile() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        // Crée un fichier de 6 Mo (au-dessus de la limite de 5 Mo)
        byte[] bigContent = new byte[6 * 1024 * 1024];
        MockMultipartFile bigFile = new MockMultipartFile(
                "photo", "big.jpg", "image/jpeg", bigContent);

        assertThatThrownBy(() -> service.uploadPhotos(messageId, List.of(bigFile)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trop volumineux");
    }
}
