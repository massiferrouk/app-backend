package com.studup.backend.service;

import com.studup.backend.model.enums.NotificationType;
import com.studup.backend.service.NotificationTemplateService.NotificationTemplate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplateServiceTest {

    private final NotificationTemplateService service = new NotificationTemplateService();

    @Test
    void shouldBuildNouveauMessageTemplate() {
        NotificationTemplate t = service.buildTemplate(
                NotificationType.NOUVEAU_MESSAGE, Map.of("prenom", "Bob"));

        assertThat(t.title()).isEqualTo("Nouveau message");
        assertThat(t.body()).contains("Bob");
    }

    @Test
    void shouldBuildAccordAccepteTemplate() {
        NotificationTemplate t = service.buildTemplate(
                NotificationType.ACCORD_ACCEPTE, Map.of("prenom", "Alice"));

        assertThat(t.title()).isEqualTo("Accord accepté !");
        assertThat(t.body()).contains("Alice");
    }

    @Test
    void shouldUseFallbackWhenPrenomAbsent() {
        NotificationTemplate t = service.buildTemplate(
                NotificationType.NOUVEAU_MESSAGE, Map.of());

        assertThat(t.body()).contains("quelqu'un");
    }

    @Test
    void shouldBuildSystemeTemplateWithCustomTitle() {
        NotificationTemplate t = service.buildTemplate(
                NotificationType.SYSTEME,
                Map.of("titre", "Maintenance", "corps", "L'app sera indisponible à 3h"));

        assertThat(t.title()).isEqualTo("Maintenance");
        assertThat(t.body()).contains("indisponible");
    }
}
