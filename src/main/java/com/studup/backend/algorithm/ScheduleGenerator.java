package com.studup.backend.algorithm;

import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.JourFerie;
import com.studup.backend.model.enums.RythmeAlternance;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Génère le calendrier d'alternance semaine par semaine à partir d'un profil.
 * Chaque entrée représente le lundi d'une semaine, labellisée 'A' (école) ou 'B' (entreprise).
 * Les jours fériés sont détectés et annotés pour permettre un override manuel.
 */
@Component
public class ScheduleGenerator {

    private static final int MAX_WEEKS = 52;

    /**
     * Génère la liste des semaines d'alternance pour un profil donné.
     *
     * @param profile    le profil alternant (rythme, dateDebut, dateFin)
     * @param joursFeries les jours fériés FR sur la période du contrat
     * @return liste ordonnée de semaines labellisées A ou B
     */
    public List<AlternanceSchedule> generateSchedule(AlternantProfile profile,
                                                      Set<JourFerie> joursFeries) {
        List<AlternanceSchedule> schedules = new ArrayList<>();

        // Ensemble des dates de jours fériés pour recherche O(1)
        Set<LocalDate> datesFeries = joursFeries.stream()
                .map(JourFerie::getDateJour)
                .collect(Collectors.toSet());

        LocalDate firstMonday = getFirstMonday(profile.getDateDebut());

        for (int i = 0; i < MAX_WEEKS; i++) {
            LocalDate semaine = firstMonday.plusWeeks(i);

            if (semaine.isAfter(profile.getDateFin())) break;

            String label = getLabelForWeek(i, profile.getRythme());
            String overrideReason = detectHolidayInWeek(semaine, datesFeries);

            schedules.add(AlternanceSchedule.builder()
                    .profile(profile)
                    .semaine(semaine)
                    .label(label)
                    .isOverridden(false)
                    .overrideReason(overrideReason)
                    .build());
        }

        return schedules;
    }

    // Retourne le lundi de la semaine contenant la date donnée.
    public LocalDate getFirstMonday(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.MONDAY) return date;
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    // Détermine le label A ou B d'une semaine selon le rythme d'alternance.
    public String getLabelForWeek(int weekIndex, RythmeAlternance rythme) {
        return switch (rythme) {
            // 1 semaine école (A), 1 semaine entreprise (B), en alternant
            case SEMAINE_1_1 -> weekIndex % 2 == 0 ? "A" : "B";
            // 2 semaines école (A) + 2 semaines entreprise (B) — provisoire,
            // l'ordre de départ sera piloté par premiereSemaine (APP-110 étape 5)
            case SEMAINE_2_2 -> weekIndex % 4 < 2 ? "A" : "B";
            // 3 semaines entreprise (B) + 1 semaine école (A)
            case SEMAINE_3_1 -> weekIndex % 4 == 3 ? "A" : "B";
            // 4 semaines école (A) + 4 semaines entreprise (B)
            case MOIS_1_1 -> weekIndex % 8 < 4 ? "A" : "B";
            // Rythme non standard : pattern 1/1 par défaut
            case AUTRE -> weekIndex % 2 == 0 ? "A" : "B";
        };
    }

    // Vérifie si un jour férié tombe dans la semaine (lundi au vendredi).
    // Retourne un message si oui, null sinon.
    private String detectHolidayInWeek(LocalDate mondayOfWeek, Set<LocalDate> datesFeries) {
        for (int day = 0; day < 5; day++) {
            LocalDate jour = mondayOfWeek.plusDays(day);
            if (datesFeries.contains(jour)) {
                return "Jour férié le " + jour;
            }
        }
        return null;
    }
}
