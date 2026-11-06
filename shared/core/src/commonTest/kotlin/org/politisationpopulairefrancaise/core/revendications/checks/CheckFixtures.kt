package org.politisationpopulairefrancaise.core.revendications.checks

import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus

/** The bundled example demands, as the checks see them (titles without the [EXEMPLE] marker). */
object CheckFixtures {
    private const val AUTHOR = "23484985405d16929a93f9aea29135678ad0ced909bf5a264f7b923d619b012f"

    private fun address(slug: String) = "30023:$AUTHOR:$slug"

    val CANTINE = DemandText(
        address("cantines-tarif-social"),
        "Un tarif de cantine à un euro pour les familles aux revenus modestes dans toutes les communes",
        "Généraliser le tarif de cantine scolaire à un euro, aujourd'hui laissé au choix de chaque commune.",
        "France",
        RevendicationStatus.NOUVELLE,
    )
    val LYON = DemandText(
        address("lyon-transports-nuit"),
        "Des transports en commun toute la nuit le week-end dans la métropole de Lyon",
        "Faire circuler métro et lignes de bus principales toute la nuit du vendredi et du samedi.",
        "Métropole de Lyon",
        RevendicationStatus.TRES_SOUTENUE,
    )
    val AGENDA = DemandText(
        address("transparence-agenda-elus"),
        "Publier l'agenda des rendez-vous des élus avec les représentants d'intérêts",
        "Chaque élu national publie, chaque semaine, la liste de ses rendez-vous avec des représentants d'intérêts : qui, quand, à quel sujet.",
        "France",
        RevendicationStatus.EN_DEBAT,
    )
    val MEDECIN = DemandText(
        address("medecin-de-garde"),
        "Un médecin de garde joignable dans chaque bassin de vie, le soir et le week-end",
        "Garantir partout une consultation médicale de garde accessible en moins de 30 minutes, en dehors des heures d'ouverture des cabinets.",
        "France",
        RevendicationStatus.NOUVELLE,
    )
    val ATELIERS = DemandText(
        address("ateliers-numeriques-mairies"),
        "Des ateliers gratuits d'apprentissage du numérique dans chaque mairie",
        "Aider chaque habitant à faire ses démarches en ligne, avec des ateliers gratuits et un accompagnement humain.",
        "France",
        RevendicationStatus.EN_DEBAT,
    )
    val CRECHES = DemandText(
        address("creches-horaires-decales"),
        "Des places en crèche pour les parents qui travaillent en horaires décalés",
        "Ouvrir des crèches tôt le matin, tard le soir et le week-end, pour les parents qui travaillent à ces heures-là.",
        "France",
        RevendicationStatus.NOUVELLE,
    )
    val ECOLES = DemandText(
        address("ecoles-chaleur-nord"),
        "Un plan de rénovation des écoles contre les fortes chaleurs dans le département du Nord",
        "Rendre les salles de classe vivables pendant les vagues de chaleur : isolation, ombrage, cours végétalisées.",
        "Nord",
        RevendicationStatus.RETENUE,
    )
    val LOGEMENTS = DemandText(
        address("logements-etudiants-marseille"),
        "Des logements étudiants à loyer modéré près des campus de Marseille",
        "Construire et rénover des résidences étudiantes à loyer modéré à proximité des universités marseillaises.",
        "Marseille",
        RevendicationStatus.CAMPAGNE,
    )

    val ALL: List<DemandText> = listOf(CANTINE, LYON, AGENDA, MEDECIN, ATELIERS, CRECHES, ECOLES, LOGEMENTS)

    fun draft(title: String, territory: String = "France", themes: List<String> = listOf("societe"), summary: String = "") =
        RevendicationDraft(title = title, summary = summary, why = "Parce que.", asks = "Ceci.", themes = themes, territory = territory)
}
