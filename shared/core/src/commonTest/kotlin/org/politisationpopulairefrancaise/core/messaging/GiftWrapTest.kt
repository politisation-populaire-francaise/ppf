package org.politisationpopulairefrancaise.core.messaging

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GiftWrapTest {

    private val now = 1_700_000_000L
    private val aliceKeys = IdentityKeys.generate()
    private val bobKeys = IdentityKeys.generate()
    private val alice = LocalMessagingSigner(aliceKeys)
    private val bob = LocalMessagingSigner(bobKeys)
    private val carol = LocalMessagingSigner(IdentityKeys.generate())

    private val message = ChatMessage.compose(alice.publicKey, listOf(bob.publicKey), "[EXEMPLE] On se retrouve devant la mairie ?", createdAt = now)

    @Test
    fun wrapHidesTheAuthorAndTheDateAndOpensOnlyForTheRecipient() = runTest {
        val rumor = message.toRumor()
        val wrap = GiftWrap.wrapFor(rumor, alice, bob.publicKey, now)

        assertEquals(EventKinds.GIFT_WRAP, wrap.kind)
        assertTrue(wrap.verify())
        assertEquals(listOf(listOf("p", bob.publicKey.hex)), wrap.tags)
        assertNotEquals(alice.publicKey.hex, wrap.pubkey)
        assertNotEquals(bob.publicKey.hex, wrap.pubkey)
        assertTrue(wrap.createdAt in (now - GiftWrap.TIMESTAMP_JITTER_SECONDS)..now)
        assertFalse(wrap.content.contains("mairie"))

        val opened = GiftWrap.unwrap(wrap, bob)
        assertEquals(rumor, opened)
        assertEquals(message, ChatMessage.fromRumor(opened))

        assertFailsWith<GiftWrapException> { GiftWrap.unwrap(wrap, carol) }
        assertFailsWith<GiftWrapException> { GiftWrap.unwrap(wrap, alice) }
    }

    @Test
    fun sealIsSignedByTheAuthorAndCarriesNoTag() = runTest {
        val rumor = message.toRumor()
        val seal = GiftWrap.seal(rumor, alice, bob.publicKey, createdAt = now - 10)
        assertEquals(EventKinds.SEAL, seal.kind)
        assertEquals(alice.publicKey.hex, seal.pubkey)
        assertEquals(emptyList(), seal.tags)
        assertEquals(now - 10, seal.createdAt)
        assertTrue(seal.verify())
        val key = bob.conversationKey(alice.publicKey)
        assertEquals(rumor, Rumor.fromJson(Nip44.decrypt(seal.content, key)))
        // Only the author of the rumor can seal it.
        assertFailsWith<IllegalArgumentException> { GiftWrap.seal(rumor, bob, alice.publicKey) }
        // Only a seal can be gift wrapped.
        assertFailsWith<IllegalArgumentException> { GiftWrap.wrap(seal.copy(kind = EventKinds.TEXT_NOTE), bob.publicKey) }
    }

    @Test
    fun anyAlterationOfTheWrapIsRejected() = runTest {
        val wrap = GiftWrap.wrapFor(message.toRumor(), alice, bob.publicKey, now)
        assertFailsWith<GiftWrapException> { GiftWrap.unwrap(wrap.copy(content = wrap.content.dropLast(1) + "B"), bob) }
        assertFailsWith<GiftWrapException> { GiftWrap.unwrap(wrap.copy(tags = listOf(listOf("p", carol.publicKey.hex))), carol) }
        assertFailsWith<GiftWrapException> { GiftWrap.unwrap(wrap.copy(kind = EventKinds.TEXT_NOTE), bob) }
        assertFailsWith<GiftWrapException> { GiftWrap.unwrap(wrap.copy(createdAt = wrap.createdAt + 1), bob) }
        // A wrap re-signed by someone else around the same content cannot be decrypted with its new key.
        val reSigned = carol.sign(UnsignedEvent.create(carol.publicKey, EventKinds.GIFT_WRAP, wrap.createdAt, wrap.tags, wrap.content))
        assertTrue(reSigned.verify())
        assertFailsWith<GiftWrapException> { GiftWrap.unwrap(reSigned, bob) }
    }

    @Test
    fun impersonationThroughTheRumorIsRejected() = runTest {
        // Mallory writes a rumor in Alice's name and seals it with her own key.
        val mallory = LocalMessagingSigner(IdentityKeys.generate())
        val forged = Rumor.of(UnsignedEvent.create(alice.publicKey, EventKinds.CHAT_MESSAGE, now, listOf(listOf("p", bob.publicKey.hex)), "[EXEMPLE] Virez tout l'argent."))
        val key = mallory.conversationKey(bob.publicKey)
        val seal = mallory.sign(UnsignedEvent.create(mallory.publicKey, EventKinds.SEAL, now, emptyList(), Nip44.encrypt(forged.toJson(), key)))
        val wrap = GiftWrap.wrap(seal, bob.publicKey, now)
        val error = assertFailsWith<GiftWrapException> { GiftWrap.unwrap(wrap, bob) }
        assertTrue(error.message!!.contains("author of the seal"))
    }

    @Test
    fun rumorWithAWrongIdOrNotASealInsideIsRejected() = runTest {
        val rumor = message.toRumor()
        val wrongId = rumor.copy(id = "0".repeat(64))
        val wrap = GiftWrap.wrapFor(wrongId, alice, bob.publicKey, now)
        assertFailsWith<GiftWrapException> { GiftWrap.unwrap(wrap, bob) }

        // A wrap whose content is a plain signed note instead of a seal.
        val note = alice.sign(UnsignedEvent.create(alice.publicKey, EventKinds.TEXT_NOTE, now, emptyList(), "not a seal"))
        val oneTime = IdentityKeys.generate()
        val content = Nip44.encrypt(note.toJson(), Nip44.conversationKey(oneTime, bob.publicKey))
        val fake = LocalMessagingSigner(oneTime).sign(UnsignedEvent.create(oneTime.publicKey, EventKinds.GIFT_WRAP, now, listOf(listOf("p", bob.publicKey.hex)), content))
        assertFailsWith<GiftWrapException> { GiftWrap.unwrap(fake, bob) }

        // A wrap whose content is not even a NIP-44 payload.
        val garbage = LocalMessagingSigner(oneTime).sign(UnsignedEvent.create(oneTime.publicKey, EventKinds.GIFT_WRAP, now, listOf(listOf("p", bob.publicKey.hex)), "bonjour"))
        assertFailsWith<GiftWrapException> { GiftWrap.unwrap(garbage, bob) }
    }

    @Test
    fun rumorJsonRoundTripsAndChecksItsId() {
        val rumor = message.toRumor()
        assertTrue(rumor.isConsistent())
        assertEquals(rumor, Rumor.fromJson(rumor.toJson()))
        assertTrue(rumor.toJson().contains("\"created_at\":$now"))
        assertFalse(rumor.toJson().contains("\"sig\""))
        assertFalse(rumor.copy(content = "autre").isConsistent())
        assertFalse(rumor.copy(id = rumor.id.uppercase()).isConsistent())
        assertFalse(rumor.copy(kind = 70000).isConsistent())
        // Fields in any order and unknown fields, as other clients may write them.
        val reordered = Rumor.fromJson("""{"content":"${rumor.content}","tags":[["p","${bob.publicKey.hex}"]],"kind":14,"created_at":$now,"pubkey":"${alice.publicKey.hex}","id":"${rumor.id}","extra":1}""")
        assertEquals(rumor, reordered)
    }

    @Test
    fun randomPastTimestampsStayWithinTwoDaysAndVary() {
        val values = (1..500).map { GiftWrap.randomPastTimestamp(now) }
        assertTrue(values.all { it in (now - GiftWrap.TIMESTAMP_JITTER_SECONDS)..now })
        assertTrue(values.distinct().size > 400)
    }

    /**
     * The worked example of the NIP-59 text: author 611df01b…, recipient 166bf376…, the rumor
     * "Are you going to the party tonight?". Both keys are published in the NIP; they are not secrets.
     */
    @Test
    fun nip59ExampleOpensWithTheRecipientKey() {
        val recipient = LocalMessagingSigner(IdentityKeys.fromHex("e108399bd8424357a710b606ae0c13166d853d327e47a6e5e038197346bdbf45")) // gitleaks:allow
        val author = LocalMessagingSigner(IdentityKeys.fromHex("0beebd062ec8735f4243466049d7747ef5d6594ee838de147f8aab842b15e273")) // gitleaks:allow
        assertEquals("166bf3765ebd1fc55decfe395beff2ea3b2a4e0a8946e7eb578512b555737c99", recipient.publicKey.hex)
        assertEquals("611df01bfcf85c26ae65453b772d8f1dfd25c264621c0277e1fc1518686faef9", author.publicKey.hex)

        val wrap = SignedEvent.fromJson(NIP59_EXAMPLE_WRAP)
        assertTrue(wrap.verify())
        assertEquals("18b1a75918f1f2c90c23da616bce317d36e348bcf5f7ba55e75949319210c87c", wrap.pubkey)

        val rumor = GiftWrap.unwrap(wrap, recipient)
        assertEquals("9dd003c6d3b73b74a85a9ab099469ce251653a7af76f523671ab828acd2a0ef9", rumor.id)
        assertEquals(author.publicKey.hex, rumor.pubkey)
        assertEquals(1, rumor.kind)
        assertEquals(1691518405, rumor.createdAt)
        assertEquals("Are you going to the party tonight?", rumor.content)
        assertEquals(emptyList(), rumor.tags)

        // The author did not keep a copy addressed to themselves in this example.
        assertFailsWith<GiftWrapException> { GiftWrap.unwrap(wrap, author) }

        // The seal quoted in the same example is authentic and decrypts to the same rumor.
        val seal = SignedEvent.fromJson(NIP59_EXAMPLE_SEAL)
        assertTrue(seal.verify())
        val key = recipient.conversationKey(author.publicKey)
        assertEquals(rumor, Rumor.fromJson(Nip44.decrypt(seal.content, key)))
    }

    private companion object {
        const val NIP59_EXAMPLE_SEAL = """{"content":"AqBCdwoS7/tPK+QGkPCadJTn8FxGkd24iApo3BR9/M0uw6n4RFAFSPAKKMgkzVMoRyR3ZS/aqATDFvoZJOkE9cPG/TAzmyZvr/WUIS8kLmuI1dCA+itFF6+ULZqbkWS0YcVU0j6UDvMBvVlGTzHz+UHzWYJLUq2LnlynJtFap5k8560+tBGtxi9Gx2NIycKgbOUv0gEqhfVzAwvg1IhTltfSwOeZXvDvd40rozONRxwq8hjKy+4DbfrO0iRtlT7G/eVEO9aJJnqagomFSkqCscttf/o6VeT2+A9JhcSxLmjcKFG3FEK3Try/WkarJa1jM3lMRQqVOZrzHAaLFW/5sXano6DqqC5ERD6CcVVsrny0tYN4iHHB8BHJ9zvjff0NjLGG/v5Wsy31+BwZA8cUlfAZ0f5EYRo9/vKSd8TV0wRb9DQ=","kind":13,"created_at":1703015180,"pubkey":"611df01bfcf85c26ae65453b772d8f1dfd25c264621c0277e1fc1518686faef9","tags":[],"id":"28a87d7c074d94a58e9e89bb3e9e4e813e2189f285d797b1c56069d36f59eaa7","sig":"02fc3facf6621196c32912b1ef53bac8f8bfe9db51c0e7102c073103586b0d29c3f39bdaa1e62856c20e90b6c7cc5dc34ca8bb6a528872cf6e65e6284519ad73"}"""

        const val NIP59_EXAMPLE_WRAP = """{"content":"AhC3Qj/QsKJFWuf6xroiYip+2yK95qPwJjVvFujhzSguJWb/6TlPpBW0CGFwfufCs2Zyb0JeuLmZhNlnqecAAalC4ZCugB+I9ViA5pxLyFfQjs1lcE6KdX3euCHBLAnE9GL/+IzdV9vZnfJH6atVjvBkNPNzxU+OLCHO/DAPmzmMVx0SR63frRTCz6Cuth40D+VzluKu1/Fg2Q1LSst65DE7o2efTtZ4Z9j15rQAOZfE9jwMCQZt27rBBK3yVwqVEriFpg2mHXc1DDwHhDADO8eiyOTWF1ghDds/DxhMcjkIi/o+FS3gG1dG7gJHu3KkGK5UXpmgyFKt+421m5o++RMD/BylS3iazS1S93IzTLeGfMCk+7IKxuSCO06k1+DaasJJe8RE4/rmismUvwrHu/HDutZWkvOAhd4z4khZo7bJLtiCzZCZ74lZcjOB4CYtuAX2ZGpc4I1iOKkvwTuQy9BWYpkzGg3ZoSWRD6ty7U+KN+fTTmIS4CelhBTT15QVqD02JxfLF7nA6sg3UlYgtiGw61oH68lSbx16P3vwSeQQpEB5JbhofW7t9TLZIbIW/ODnI4hpwj8didtk7IMBI3Ra3uUP7ya6vptkd9TwQkd/7cOFaSJmU+BIsLpOXbirJACMn+URoDXhuEtiO6xirNtrPN8jYqpwvMUm5lMMVzGT3kMMVNBqgbj8Ln8VmqouK0DR+gRyNb8fHT0BFPwsHxDskFk5yhe5c/2VUUoKCGe0kfCcX/EsHbJLUUtlHXmTqaOJpmQnW1tZ/siPwKRl6oEsIJWTUYxPQmrM2fUpYZCuAo/29lTLHiHMlTbarFOd6J/ybIbICy2gRRH/LFSryty3Cnf6aae+A9uizFBUdCwTwffc3vCBae802+R92OL78bbqHKPbSZOXNC+6ybqziezwG+OPWHx1Qk39RYaF0aFsM4uZWrFic97WwVrH5i+/Nsf/OtwWiuH0gV/SqvN1hnkxCTF/+XNn/laWKmS3e7wFzBsG8+qwqwmO9aVbDVMhOmeUXRMkxcj4QreQkHxLkCx97euZpC7xhvYnCHarHTDeD6nVK+xzbPNtzeGzNpYoiMqxZ9bBJwMaHnEoI944Vxoodf51cMIIwpTmmRvAzI1QgrfnOLOUS7uUjQ/IZ1Qa3lY08Nqm9MAGxZ2Ou6R0/Z5z30ha/Q71q6meAs3uHQcpSuRaQeV29IASmye2A2Nif+lmbhV7w8hjFYoaLCRsdchiVyNjOEM4VmxUhX4VEvw6KoCAZ/XvO2eBF/SyNU3Of4SO","kind":1059,"created_at":1703021488,"pubkey":"18b1a75918f1f2c90c23da616bce317d36e348bcf5f7ba55e75949319210c87c","id":"5c005f3ccf01950aa8d131203248544fb1e41a0d698e846bd419cec3890903ac","sig":"35fabdae4634eb630880a1896a886e40fd6ea8a60958e30b89b33a93e6235df750097b04f9e13053764251b8bc5dd7e8e0794a3426a90b6bcc7e5ff660f54259","tags":[["p","166bf3765ebd1fc55decfe395beff2ea3b2a4e0a8946e7eb578512b555737c99"]]}"""
    }
}
