package org.politisationpopulairefrancaise.core.agir.poster

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaFixtures
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.calls.CallFixtures
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import org.politisationpopulairefrancaise.core.agir.map.Gzip
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The code of a poster (AG-61): the signed event travels inside it and is checked on reading; a
 * changed event is refused; beyond the size of a code readable on A4, the stable address, without
 * any parameter, takes its place. All data are fictitious ([EXEMPLE]).
 */
class PosterQrTest {
    private val fixtures = AgendaFixtures()

    @Test
    fun base45FollowsTheExamplesOfRfc9285() {
        assertEquals("BB8", Base45.encode("AB".encodeToByteArray()))
        assertEquals("%69 VD92EX0", Base45.encode("Hello!!".encodeToByteArray()))
        assertEquals("UJCLQE7W581", Base45.encode("base-45".encodeToByteArray()))
        assertEquals("ietf!", Base45.decode("QED8WEX0")?.decodeToString())
        assertEquals("", Base45.decode("")?.decodeToString())
        // 65 536 does not fit two bytes; a lone character, a lower case letter or a sign outside the alphabet is invalid.
        assertNull(Base45.decode("GGW"))
        assertNull(Base45.decode("BB8A"))
        assertNull(Base45.decode("bb8"))
        assertNull(Base45.decode("BB#"))
        val bytes = ByteArray(256) { it.toByte() }
        assertTrue(bytes.contentEquals(Base45.decode(Base45.encode(bytes))))
    }

    @Test
    fun aMobilizationTravelsInsideTheCodeAndIsVerifiedOnReading() = runTest {
        val mobilization = fixtures.mobilization(summary = "[EXEMPLE] Résumé de démonstration, avec des accents : é, à, ç.")
        val payload = assertIs<PosterPayload.Event>(PosterQr.payloadFor(mobilization.event, AgirLinks.urlOf(mobilization)))
        assertTrue(payload.text.startsWith("PPF:EVENT:1:"))
        assertTrue(payload.text.length <= PosterQr.MAX_CHARACTERS)
        // Only characters of the alphanumeric mode of QR codes, the prefix included.
        assertTrue(payload.text.all { it in Base45.ALPHABET }, payload.text)
        val read = assertIs<PosterScan.Event>(PosterQr.read(payload.text))
        assertEquals(mobilization.event, read.event)
        assertEquals(mobilization.event.toJson(), read.event.toJson(), "the exact signed JSON")
        assertEquals(mobilization.address, Mobilization.from(read.event)?.address)
        // Nothing about the person who makes the poster: the code is the event, as its author signed it.
        assertEquals(PosterQr.PREFIX + Base45.encode(assertNotNull(Gzip.compress(mobilization.event.toJson().encodeToByteArray()))), payload.text)
    }

    @Test
    fun aChangedOrDamagedEventIsRefused() = runTest {
        val mobilization = fixtures.mobilization()
        // The same id and signature with another place: what a forger would print.
        val moved = mobilization.event.copy(tags = mobilization.event.tags.map { if (it[0] == "location") listOf("location", "[EXEMPLE] Ailleurs") else it })
        assertEquals(PosterScan.Forged, PosterQr.read(codeOf(moved.toJson())))
        val retold = mobilization.event.copy(content = "[EXEMPLE] Un autre texte")
        assertEquals(PosterScan.Forged, PosterQr.read(codeOf(retold.toJson())))
        // Signed again by another key, the event is authentic, but that key's: the screen says who signed it.
        val other = AgendaFixtures().mobilization()
        val read = assertIs<PosterScan.Event>(PosterQr.read(assertNotNull(PosterQr.encodeEvent(other.event))))
        assertEquals(other.author, read.event.pubkey)
        assertFalse(read.event.pubkey == mobilization.author)
        // A cut code, a code that is not Base45, gzip or JSON is damaged, never an event.
        val good = assertNotNull(PosterQr.encodeEvent(mobilization.event))
        assertEquals(PosterScan.Damaged, PosterQr.read(good.dropLast(9)))
        assertEquals(PosterScan.Damaged, PosterQr.read(PosterQr.PREFIX + "abc"))
        assertEquals(PosterScan.Damaged, PosterQr.read(PosterQr.PREFIX))
        assertEquals(PosterScan.Damaged, PosterQr.read(PosterQr.PREFIX + Base45.encode("pas du gzip".encodeToByteArray())))
        assertEquals(PosterScan.Damaged, PosterQr.read(codeOf("{\"pas\":\"un événement\"}")))
        // Anything else opens nothing.
        assertEquals(PosterScan.NotPpf, PosterQr.read("Bonjour"))
        assertEquals(PosterScan.NotPpf, PosterQr.read("https://exemple.invalid/agir/agenda/${mobilization.author}/rassemblement-test"))
        assertEquals(PosterScan.NotPpf, PosterQr.read("ppf:event:1:" + good.removePrefix(PosterQr.PREFIX)))
    }

    @Test
    fun aLongEventFallsBackToItsAddressWithoutAnyParameter() = runTest {
        // Letters drawn at random barely compress: well beyond a code readable on an A4 poster.
        val random = Random(7)
        val text = "[EXEMPLE] " + (1..600).joinToString(" ") { (1..5).map { 'a' + random.nextInt(26) }.joinToString("") }
        val long = fixtures.mobilization(description = text)
        assertNull(PosterQr.encodeEvent(long.event))
        val payload = assertIs<PosterPayload.Address>(PosterQr.payloadFor(long.event, AgirLinks.urlOf(long)))
        assertEquals("https://politisation-populaire-francaise.org/agir/agenda/${fixtures.pubkey}/rassemblement-test", payload.text)
        assertFalse('?' in payload.text || '#' in payload.text, "no parameter, no fragment")
        val read = assertIs<PosterScan.Address>(PosterQr.read(payload.text))
        assertEquals(long.address, read.address)
        assertIs<PosterPayload.Address>(PosterContent.of(long).payload)
    }

    @Test
    fun onlyTheExactAddressesOfPpfAreFollowed() = runTest {
        val mobilization = fixtures.mobilization()
        val url = AgirLinks.urlOf(mobilization)
        assertEquals(mobilization.address, AgirLinks.parse(url))
        assertEquals(mobilization.address, AgirLinks.parse(url.replace("https://", "https://www.")))
        assertEquals(mobilization.address, AgirLinks.parse(url.replace(".org/", ".fr/")), "a reserved domain of PPF")
        // Parameters, fragments, http, look-alike hosts, a user name before the host, other paths: refused.
        assertNull(AgirLinks.parse("$url?utm_source=affiche"))
        assertNull(AgirLinks.parse("$url#lieu"))
        assertNull(AgirLinks.parse(url.replace("https://", "http://")))
        assertNull(AgirLinks.parse(url.replace("politisation-populaire-francaise.org", "politisation-populaire-francaise.org.piege.example")))
        assertNull(AgirLinks.parse(url.replace("https://", "https://politisation-populaire-francaise.org@")))
        assertNull(AgirLinks.parse(url.replace("https://politisation-populaire-francaise.org", "https://politisation-populaire-francaise.org:8443")))
        assertNull(AgirLinks.parse("$url/plus"))
        assertNull(AgirLinks.parse(url.replace("/agir/agenda/", "/agir/autre/")))
        assertNull(AgirLinks.parse(url.replace(mobilization.author, mobilization.author.uppercase())))
        assertEquals(PosterScan.NotPpf, PosterQr.read("$url?utm_source=affiche"))
    }

    @Test
    fun aCallHasItsOwnAddressAndTravelsSignedToo() = runTest {
        val keys = IdentityKeys.generate()
        val draft = CallFixtures.validDraft(CallType.SE_REUNIR)
        val event = LocalEventSigner(keys).sign(draft.toUnsignedEvent(keys.publicKey, CallFixtures.NOW, "reunion-loyers-1a2b"))
        val call = assertNotNull(Call.from(event))
        val url = AgirLinks.urlOf(call)
        assertEquals("https://politisation-populaire-francaise.org/agir/appels/${keys.publicKey.hex}/reunion-loyers-1a2b", url)
        assertEquals(call.address, AgirLinks.parse(url))
        assertEquals(Call.KIND, AgirLinks.parse(url)?.kind)
        val payload = assertIs<PosterPayload.Event>(PosterQr.payloadFor(call.event, url))
        val read = assertIs<PosterScan.Event>(PosterQr.read(payload.text))
        assertEquals(call.address, Call.from(read.event)?.address)
    }

    private fun codeOf(json: String): String = PosterQr.PREFIX + Base45.encode(assertNotNull(Gzip.compress(json.encodeToByteArray())))
}
