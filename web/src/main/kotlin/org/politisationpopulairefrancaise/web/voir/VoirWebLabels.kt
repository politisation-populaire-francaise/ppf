package org.politisationpopulairefrancaise.web.voir

import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.voir.FactKind
import org.politisationpopulairefrancaise.core.voir.FactStatus
import org.politisationpopulairefrancaise.core.voir.ImpactDirection
import org.politisationpopulairefrancaise.core.voir.LibertyCategory
import org.politisationpopulairefrancaise.core.voir.ProcedureStage
import org.politisationpopulairefrancaise.core.voir.PromiseStatus
import org.politisationpopulairefrancaise.core.voir.PublicRole
import org.politisationpopulairefrancaise.core.voir.RecourseStatus
import org.politisationpopulairefrancaise.core.voir.Reliability
import org.politisationpopulairefrancaise.core.voir.SourceKind
import org.politisationpopulairefrancaise.core.voir.TextStage
import org.politisationpopulairefrancaise.core.voir.VoirAxis
import org.politisationpopulairefrancaise.core.voir.VotePosition

/*
 * The French labels of the vocabulary of "Voir" for the web site: the same words as the
 * applications (shared/ui, strings_voir.xml), kept here because the site does not read the
 * resources of the Compose interface.
 */

/** The colour family of a badge (classes of site.css); the colour never carries the meaning alone. */
enum class BadgeTone(val css: String) {
    NEUTRAL("badge"),
    BRAND("badge badge-brand"),
    SUCCESS("badge badge-success"),
    WARNING("badge badge-warning"),
    ACCENT("badge badge-accent"),
}

internal val VoirAxis.label: String get() = when (this) {
    VoirAxis.MOUVEMENT -> "Le mouvement"
    VoirAxis.POUVOIR -> "Le pouvoir"
    VoirAxis.LIBERTES -> "Libertés et contre-pouvoirs"
}

internal val VoirAxis.intro: String get() = when (this) {
    VoirAxis.MOUVEMENT -> "Les chiffres de la mobilisation et de la répression, la chronologie, les mouvements dans le monde."
    VoirAxis.POUVOIR -> "Les élus et les ministres, leurs votes, leurs promesses, leurs affaires, et les textes en discussion."
    VoirAxis.LIBERTES -> "Les libertés publiques et les contre-pouvoirs : Parlement, justice, médias."
}

internal val SourceKind.label: String get() = when (this) {
    SourceKind.AUTHORITIES -> "Autorités"
    SourceKind.ORGANISERS -> "Organisateurs"
    SourceKind.INDEPENDENT -> "Comptage indépendant"
    SourceKind.MEDIA -> "Médias"
    SourceKind.CITIZENS -> "Signalements vérifiés"
    SourceKind.OFFICIAL_TEXT -> "Texte officiel"
    SourceKind.JUSTICE -> "Décision de justice"
    SourceKind.PARLIAMENT -> "Parlement"
    SourceKind.REPORT -> "Rapport officiel"
    SourceKind.STATEMENT -> "Déclaration publique"
    SourceKind.INVESTIGATION -> "Enquête de presse"
}

internal val Reliability.label: String get() = when (this) {
    Reliability.HIGH -> "Fiabilité élevée"
    Reliability.MEDIUM -> "Fiabilité moyenne"
    Reliability.LOW -> "Fiabilité faible"
}

internal val Reliability.tone: BadgeTone get() = when (this) {
    Reliability.HIGH -> BadgeTone.SUCCESS
    Reliability.MEDIUM -> BadgeTone.BRAND
    Reliability.LOW -> BadgeTone.WARNING
}

internal val Reliability.explanation: String get() = when (this) {
    Reliability.HIGH -> "Fiabilité élevée : source primaire, ou comptage dont la méthode est publique et vérifiable."
    Reliability.MEDIUM -> "Fiabilité moyenne : source connue dont la méthode n’est que partiellement publiée."
    Reliability.LOW -> "Fiabilité faible : déclarations non vérifiables ; le chiffre est montré pour ce qu’il est, jamais mélangé aux autres."
}

internal val FactStatus.label: String get() = when (this) {
    FactStatus.ESTABLISHED -> "Établi"
    FactStatus.ALLEGED -> "Allégué"
    FactStatus.UNDER_INVESTIGATION -> "En cours d’enquête"
}

internal val FactStatus.tone: BadgeTone get() = when (this) {
    FactStatus.ESTABLISHED -> BadgeTone.SUCCESS
    FactStatus.ALLEGED -> BadgeTone.WARNING
    FactStatus.UNDER_INVESTIGATION -> BadgeTone.BRAND
}

internal val FactStatus.explanation: String get() = when (this) {
    FactStatus.ESTABLISHED -> "Établi : prouvé par une source primaire (décision de justice, Journal officiel, compte rendu parlementaire, rapport officiel) ou par plusieurs sources concordantes."
    FactStatus.ALLEGED -> "Allégué : affirmé par une source identifiée, sans être encore prouvé."
    FactStatus.UNDER_INVESTIGATION -> "En cours d’enquête : une enquête est ouverte, rien n’est établi à ce stade."
}

internal val FactKind.label: String get() = when (this) {
    FactKind.MOBILISATION -> "Mobilisation"
    FactKind.OFFICIAL_STATEMENT -> "Déclaration officielle"
    FactKind.OFFICIAL_DECISION -> "Décision officielle"
    FactKind.GOVERNMENT_RESPONSE -> "Réponse du gouvernement"
    FactKind.VOTE -> "Vote"
    FactKind.PROMISE -> "Promesse"
    FactKind.STATEMENT_VS_VOTE -> "Discours et vote"
    FactKind.CASE -> "Affaire judiciaire"
    FactKind.CONTRADICTED_STATEMENT -> "Déclaration contredite"
    FactKind.PUBLIC_MONEY -> "Argent public"
    FactKind.INTERESTS -> "Intérêts"
    FactKind.LOBBYING -> "Influence"
    FactKind.LIBERTY -> "Libertés publiques"
}

/** The section of a person's sheet that lists the facts of this kind (identical for everyone). */
internal val FactKind.section: String get() = when (this) {
    FactKind.VOTE -> "Votes"
    FactKind.PROMISE -> "Promesses"
    FactKind.STATEMENT_VS_VOTE -> "Discours et votes"
    FactKind.CASE -> "Affaires judiciaires"
    FactKind.CONTRADICTED_STATEMENT -> "Déclarations contredites par les faits"
    FactKind.INTERESTS -> "Intérêts et pantouflage"
    FactKind.LOBBYING -> "Rencontres avec des représentants d’intérêts"
    FactKind.PUBLIC_MONEY -> "Argent public"
    else -> label
}

internal val ProcedureStage.label: String get() = when (this) {
    ProcedureStage.INVESTIGATION -> "Enquête en cours"
    ProcedureStage.INDICTMENT -> "Mise en examen"
    ProcedureStage.TRIAL -> "Procès en cours"
    ProcedureStage.FIRST_INSTANCE_CONVICTION -> "Condamnation en première instance, appel possible"
    ProcedureStage.APPEAL -> "Procès en appel"
    ProcedureStage.FINAL_CONVICTION -> "Condamnation définitive"
    ProcedureStage.ACQUITTAL -> "Relaxe ou acquittement"
    ProcedureStage.DISMISSAL -> "Non-lieu"
    ProcedureStage.NO_FURTHER_ACTION -> "Classement sans suite"
}

internal val ProcedureStage.tone: BadgeTone get() = when {
    this == ProcedureStage.FINAL_CONVICTION -> BadgeTone.ACCENT
    endedWithoutConviction -> BadgeTone.SUCCESS
    else -> BadgeTone.WARNING
}

internal val ProcedureStage.explanation: String get() = when (this) {
    ProcedureStage.INVESTIGATION -> "Enquête : la police ou la gendarmerie recherche des preuves sous l’autorité du procureur ou d’un juge d’instruction ; personne n’est encore jugé."
    ProcedureStage.INDICTMENT -> "Mise en examen : un juge d’instruction estime qu’il existe des indices graves ou concordants (code de procédure pénale, article 80-1). Ce n’est pas une condamnation."
    ProcedureStage.TRIAL -> "Procès : la personne est jugée par un tribunal."
    ProcedureStage.FIRST_INSTANCE_CONVICTION -> "Condamnation en première instance : un tribunal a condamné la personne, qui peut faire appel ; la décision n’est pas définitive."
    ProcedureStage.APPEAL -> "Appel : l’affaire est jugée une seconde fois, par une cour d’appel."
    ProcedureStage.FINAL_CONVICTION -> "Condamnation définitive : plus aucun recours n’est possible."
    ProcedureStage.ACQUITTAL -> "Relaxe ou acquittement : la justice a jugé que la personne n’était pas coupable."
    ProcedureStage.DISMISSAL -> "Non-lieu : le juge d’instruction met fin à l’affaire sans procès, faute de charges suffisantes."
    ProcedureStage.NO_FURTHER_ACTION -> "Classement sans suite : le procureur décide de ne pas poursuivre."
}

internal val PromiseStatus.label: String get() = when (this) {
    PromiseStatus.KEPT -> "Tenue"
    PromiseStatus.IN_PROGRESS -> "En cours"
    PromiseStatus.ABANDONED -> "Abandonnée"
    PromiseStatus.CONTRARY -> "Contredite par les actes"
}

internal val PromiseStatus.tone: BadgeTone get() = when (this) {
    PromiseStatus.KEPT -> BadgeTone.SUCCESS
    PromiseStatus.IN_PROGRESS -> BadgeTone.BRAND
    PromiseStatus.ABANDONED -> BadgeTone.WARNING
    PromiseStatus.CONTRARY -> BadgeTone.ACCENT
}

internal val VotePosition.label: String get() = when (this) {
    VotePosition.FOR -> "Pour"
    VotePosition.AGAINST -> "Contre"
    VotePosition.ABSTENTION -> "Abstention"
    VotePosition.NOT_VOTING -> "Non-votant"
    VotePosition.ABSENT -> "Absent"
}

internal val LibertyCategory.label: String get() = when (this) {
    LibertyCategory.PROTEST_BAN -> "Interdictions de manifester"
    LibertyCategory.DISSOLUTION -> "Dissolutions"
    LibertyCategory.RESTRICTIVE_LAW -> "Lois et décrets restrictifs"
    LibertyCategory.SURVEILLANCE -> "Surveillance"
    LibertyCategory.OTHER -> "Autres mesures"
}

internal val RecourseStatus.label: String get() = when (this) {
    RecourseStatus.NONE -> "Aucun recours"
    RecourseStatus.PENDING -> "Recours en cours"
    RecourseStatus.SUSPENDED -> "Suspendu par le juge"
    RecourseStatus.ANNULLED -> "Annulé par le juge"
    RecourseStatus.REJECTED -> "Recours rejeté"
}

internal val PublicRole.label: String get() = when (this) {
    PublicRole.DEPUTY -> "Députés"
    PublicRole.SENATOR -> "Sénateurs"
    PublicRole.MEP -> "Députés européens"
    PublicRole.MINISTER -> "Ministres"
    PublicRole.PRESIDENT -> "Présidence"
    PublicRole.MAYOR -> "Maires"
    PublicRole.LOCAL_ELECTED -> "Élus locaux"
    PublicRole.OTHER -> "Autres"
}

internal val TextStage.label: String get() = when (this) {
    TextStage.TABLED -> "Dépôt"
    TextStage.COMMITTEE -> "Commission"
    TextStage.ASSEMBLY -> "Assemblée nationale"
    TextStage.SENATE -> "Sénat"
    TextStage.JOINT_COMMITTEE -> "Commission mixte paritaire"
    TextStage.FINAL_READING -> "Lecture définitive"
    TextStage.ADOPTED -> "Adopté"
    TextStage.CONSTITUTIONAL_COUNCIL -> "Conseil constitutionnel"
    TextStage.PROMULGATED -> "Promulgué"
    TextStage.REJECTED -> "Rejeté"
    TextStage.WITHDRAWN -> "Retiré"
}

internal val ImpactDirection.label: String get() = when (this) {
    ImpactDirection.GAINS -> "Y gagne"
    ImpactDirection.LOSES -> "Y perd"
    ImpactDirection.MIXED -> "Effet mitigé"
}

internal val ImpactDirection.tone: BadgeTone get() = when (this) {
    ImpactDirection.GAINS -> BadgeTone.SUCCESS
    ImpactDirection.LOSES -> BadgeTone.ACCENT
    ImpactDirection.MIXED -> BadgeTone.WARNING
}

internal val ContentOrigin.label: String get() = when (this) {
    ContentOrigin.HUMAN -> "Rédigé par l’équipe"
    ContentOrigin.AI_ASSISTED -> "Rédigé avec l’aide d’une IA, relu par l’équipe"
}

internal const val PRESUMPTION_NOTICE: String =
    "Présomption d’innocence : tant qu’elle n’a pas été condamnée définitivement, la personne est présumée innocente."

internal const val PRESUMPTION_LAW: String =
    "Toute personne est présumée innocente tant que sa culpabilité n’a pas été établie par une condamnation définitive (Déclaration des droits de l’homme et du citoyen, article 9 ; code civil, article 9-1)."

internal const val SIDE_BY_SIDE_NOTE: String =
    "Les sources ne sont jamais fusionnées : chacune est affichée avec son propre chiffre."
