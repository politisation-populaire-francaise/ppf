package org.politisationpopulairefrancaise.core.agir.harassment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 9 November 2026, 05:53 in Paris. */
private const val TODAY = 1_794_200_000L

private fun evidence(url: String = "https://reseau.example.org/publication/4821", text: String = "[EXEMPLE] On sait où tu habites.", withImage: Boolean = true): Evidence {
    val content = EvidenceContent(
        seenAt = TODAY - 3_200,
        keptAt = TODAY,
        platform = "[EXEMPLE] Réseau social",
        url = url,
        text = text,
        note = "[EXEMPLE] note personnelle",
        image = if (withImage) EvidenceImage("a".repeat(64), "b".repeat(64), 2_048, "image/png", EvidenceFormat.SOURCE_PICKER) else null,
    )
    return Evidence("0123456789abcdef0123456789abcdef", content, EvidenceFormat.digestOf(content), if (withImage) "fedcba9876543210fedcba9876543210" else null)
}

private val complete = ReportInput(
    evidence = listOf(evidence()),
    explanation = "[EXEMPLE] Ce compte publie mon adresse et des menaces depuis trois jours.",
    offences = setOf(Offence.HARASSMENT, Offence.DOXXING),
    name = "[EXEMPLE] Camille Exemple",
    email = "camille@exemple.invalid",
    goodFaith = true,
    city = "[EXEMPLE] Lyon",
)

/** AG-59: the templates say what the law asks for, filled from the evidence, never more. */
class HarassmentReportsTest {
    @Test
    fun theDsaNoticeContainsEveryElementOfArticle16() {
        assertTrue(HarassmentReports.dsaMissing(complete).isEmpty())
        val notice = HarassmentReports.dsaNotice(complete, TODAY)
        // a) the explanation, b) the exact location, c) the name and the email address, d) the good faith statement.
        assertTrue("Ce compte publie mon adresse" in notice)
        assertTrue("https://reseau.example.org/publication/4821" in notice)
        assertTrue("vu le 9 novembre 2026 à 05:00 (heure de Paris)" in notice, notice)
        assertTrue("Nom : [EXEMPLE] Camille Exemple" in notice)
        assertTrue("Adresse électronique : camille@exemple.invalid" in notice)
        assertTrue(HarassmentReports.GOOD_FAITH in notice)
        assertFalse("[À compléter" in notice)
        assertTrue("article 16" in notice && "2022/2065" in notice)
        assertTrue("article 222-33-2-2" in notice && "article 223-1-1" in notice)
        assertTrue("« [EXEMPLE] On sait où tu habites. »" in notice)
        // The personal note and the digests are for the person, not for the platform.
        assertFalse("note personnelle" in notice)
        assertFalse(complete.evidence.single().digest in notice)
    }

    @Test
    fun whatIsMissingIsSaidAndTheGoodFaithIsNeverStatedForThePerson() {
        val bare = ReportInput(evidence = listOf(evidence(url = "")))
        assertEquals(DsaElement.entries.toSet(), HarassmentReports.dsaMissing(bare))
        val notice = HarassmentReports.dsaNotice(bare, TODAY)
        assertTrue("[Indiquez l’adresse exacte (URL) du contenu." in notice)
        assertTrue("[votre nom]" in notice && "[votre adresse électronique]" in notice)
        // Not ticked: the statement is only offered between brackets, for the person to write if it is true.
        assertTrue("[À compléter par vous seulement si c’est vrai" in notice)
        assertEquals(setOf(DsaElement.NAME_AND_EMAIL), HarassmentReports.dsaMissing(complete.copy(email = "camille")))
        assertEquals(setOf(DsaElement.GOOD_FAITH), HarassmentReports.dsaMissing(complete.copy(goodFaith = false)))
        assertEquals(setOf(DsaElement.LOCATION), HarassmentReports.dsaMissing(complete.copy(evidence = listOf(evidence(url = "")))))
        assertEquals(setOf(DsaElement.EXPLANATION), HarassmentReports.dsaMissing(complete.copy(explanation = " ")))
    }

    @Test
    fun theComplaintToTheProsecutorHasWhatServicePublicAsksFor() {
        val letter = HarassmentReports.complaint(complete, TODAY)
        assertTrue("Madame, Monsieur le procureur de la République" in letter)
        assertTrue("Tribunal judiciaire de [EXEMPLE] Lyon" in letter)
        assertTrue("Objet : plainte contre X" in letter)
        assertTrue("article 222-33-2-2" in letter && "article 223-1-1" in letter)
        assertTrue("Les faits" in letter && "Témoins" in letter && "Préjudice" in letter && "Pièces jointes" in letter)
        assertTrue("[Votre adresse postale]" in letter)
        assertTrue("Lettre recommandée avec avis de réception" in letter)
        // Each piece of evidence with its date, its address and its digests.
        val kept = complete.evidence.single()
        assertTrue("Empreinte SHA-256 du fichier : " + "a".repeat(64) in letter)
        assertTrue(kept.digest in letter)
        assertTrue("Vu le 9 novembre 2026 à 05:00 (heure de Paris) ([EXEMPLE] Réseau social)" in letter, letter)
        // A known author replaces X.
        assertTrue("Objet : plainte contre [EXEMPLE] le compte @exemple" in HarassmentReports.complaint(complete.copy(author = "[EXEMPLE] le compte @exemple"), TODAY))
        // Without any offence ticked, no qualification is suggested.
        val plain = HarassmentReports.complaint(complete.copy(offences = emptySet()), TODAY)
        assertFalse("222-33-2-2" in plain)
    }

    @Test
    fun thePharosElementsCarryTheAddressTheDateAndThePlatform() {
        val pharos = HarassmentReports.pharos(complete)
        assertTrue("internet-signalement.gouv.fr" in pharos)
        assertTrue("Adresse (URL) : https://reseau.example.org/publication/4821" in pharos)
        assertTrue("Vu le 9 novembre 2026 à 05:00" in pharos)
        assertTrue("[EXEMPLE] Réseau social" in pharos)
        assertTrue("harcèlement en ligne ; divulgation d’informations personnelles" in pharos)
        // Nothing of the person's identity: the contact details of PHAROS are optional.
        assertFalse("Camille" in pharos)
        assertFalse("camille@exemple.invalid" in pharos)
    }

    @Test
    fun theTemplatesUseNoEmDash() {
        val bare = ReportInput()
        for (text in listOf(
            HarassmentReports.dsaNotice(complete, TODAY), HarassmentReports.dsaNotice(bare, TODAY),
            HarassmentReports.complaint(complete, TODAY), HarassmentReports.complaint(bare, TODAY),
            HarassmentReports.pharos(complete), HarassmentReports.pharos(bare),
        )) assertFalse('—' in text)
    }
}
