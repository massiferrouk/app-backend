package com.studup.backend.algorithm;

import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.JourFerie;
import com.studup.backend.model.enums.PremiereSemaine;
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

            String label = getLabelForWeek(i, profile.getRythme(), profile.getPremiereSemaine());
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

    /**
     * Détermine le label A (école) ou B (entreprise) d'une semaine (APP-110).
     * Le rythme fixe la longueur des blocs école/entreprise, [premiereSemaine]
     * fixe lequel des deux blocs ouvre le cycle — c'est ce qui permet de
     * représenter les rythmes inversés (ex. 1 école PUIS 3 entreprise).
     *
     * [premiereSemaine] null (profils d'avant la migration V24, anciens tests) :
     * on retombe sur l'ordre historiquement codé en dur via defaultFor().
     */
    public String getLabelForWeek(int weekIndex, RythmeAlternance rythme,
                                  PremiereSemaine premiereSemaine) {
        PremiereSemaine ordre = premiereSemaine != null
                ? premiereSemaine
                : PremiereSemaine.defaultFor(rythme);

        // Longueur des blocs {école, entreprise} par cycle
        int blocEcole = switch (rythme) {
            case SEMAINE_1_1, AUTRE -> 1;   // AUTRE : approximation 1/1 par défaut
            case SEMAINE_2_2 -> 2;
            case SEMAINE_3_1 -> 1;          // toujours 1 école pour 3 entreprise
            case MOIS_1_1 -> 4;
        };
        int blocEntreprise = switch (rythme) {
            case SEMAINE_1_1, AUTRE -> 1;
            case SEMAINE_2_2 -> 2;
            case SEMAINE_3_1 -> 3;
            case MOIS_1_1 -> 4;
        };

        int cycle = blocEcole + blocEntreprise;
        int position = weekIndex % cycle;

        // Le bloc qui ouvre le cycle dépend de l'ordre choisi par l'alternant
        int longueurPremierBloc = ordre == PremiereSemaine.ECOLE ? blocEcole : blocEntreprise;
        String labelPremierBloc = ordre == PremiereSemaine.ECOLE ? "A" : "B";
        String labelSecondBloc = ordre == PremiereSemaine.ECOLE ? "B" : "A";

        return position < longueurPremierBloc ? labelPremierBloc : labelSecondBloc;
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
