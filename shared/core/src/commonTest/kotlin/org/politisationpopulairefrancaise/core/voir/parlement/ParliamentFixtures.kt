package org.politisationpopulairefrancaise.core.voir.parlement

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.OpenDataDownload
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * Fictional packages in the format of the contract: every person is "[EXEMPLE] Prénom Nom",
 * every identifier invented. Signed with a key generated for the test.
 */
internal object ParliamentFixtures {

    val members = MembersPackage(
        example = true,
        members = listOf(
            Member("an:PA900002", "[EXEMPLE] Zoé Vasseur", Chamber.AN, "an:PO900001", "6901", "1re circonscription du Rhône", "2024-07-18", "https://www.assemblee-nationale.fr/dyn/deputes/PA900002"),
            Member("an:PA900001", "[EXEMPLE] Élise Arnaud", Chamber.AN, "an:PO900002", "2A01", "1re circonscription de Corse-du-Sud", "2024-07-18", "https://www.assemblee-nationale.fr/dyn/deputes/PA900001", interestsUrl = "https://www.hatvp.fr/fiche-nominative/?declarant=exemple"),
            Member("an:PA900003", "[EXEMPLE] Bruno Côté", Chamber.AN, "an:PO900001", "7501", "1re circonscription de Paris", "2026-10-15", "https://www.assemblee-nationale.fr/dyn/deputes/PA900003"),
            Member("senat:90001A", "[EXEMPLE] Camille Durand", Chamber.SENAT, "senat:GRP1", "33", "Gironde", "2023-10-01", "https://www.senat.fr/senateur/exemple.html"),
            Member("pe:990001", "[EXEMPLE] Dominique Étienne", Chamber.PE, "pe:GRP1", "FR", "France", "2024-07-16", "https://www.europarl.europa.eu/meps/fr/990001", nationalParty = "[EXEMPLE] Parti d'exemple"),
        ),
        groups = listOf(
            Group("an:PO900001", Chamber.AN, "[EXEMPLE] Groupe des tilleuls", "GT"),
            Group("an:PO900002", Chamber.AN, "[EXEMPLE] Groupe des acacias", "GA"),
            Group("senat:GRP1", Chamber.SENAT, "[EXEMPLE] Groupe sénatorial", "GS"),
            Group("pe:GRP1", Chamber.PE, "[EXEMPLE] Groupe européen", "GE"),
        ),
    )

    val octobre = BallotsPackage(
        example = true,
        chamber = Chamber.AN,
        month = "2026-10",
        ballots = listOf(
            Ballot(
                id = "an:17:101", number = 101, date = "2026-10-08", title = "[EXEMPLE] l'ensemble du projet de loi sur les tilleuls", kind = "SPS", adopted = true,
                counts = Counts(voters = 2, expressed = 2, inFavour = 1, against = 1, abstention = 0, notVoting = 0),
                groups = listOf(GroupCount("an:PO900001", 1, 0, 0), GroupCount("an:PO900002", 0, 1, 0)),
                positions = Positions(inFavour = listOf("an:PA900002"), against = listOf("an:PA900001"), delegated = listOf("an:PA900001")),
                url = "https://www.assemblee-nationale.fr/dyn/17/scrutins/101", dossier = "an-dossier:DLR5L17N90001", dossierUrl = "https://www.assemblee-nationale.fr/dyn/17/dossiers/exemple",
            ),
            Ballot(
                id = "an:17:102", number = 102, date = "2026-10-20", title = "[EXEMPLE] l'amendement n° 12 sur les acacias", kind = "SPO", adopted = false,
                counts = Counts(voters = 1, expressed = 1, inFavour = 0, against = 1, abstention = 0, notVoting = 1),
                groups = listOf(GroupCount("an:PO900001", 0, 1, 0), GroupCount("an:PO900002", 0, 0, 0, notVoting = 1)),
                positions = Positions(
                    against = listOf("an:PA900002"),
                    notVoting = listOf("an:PA900001"),
                    corrections = listOf(VoteCorrection("an:PA900002", Position.FOR)),
                    notVotingCauses = listOf(NotVotingCause("an:PA900001", "PSE")),
                ),
                url = "https://www.assemblee-nationale.fr/dyn/17/scrutins/102",
            ),
        ),
    )

    val novembre = BallotsPackage(
        example = true,
        chamber = Chamber.AN,
        month = "2026-11",
        ballots = listOf(
            Ballot(
                id = "an:17:103", number = 103, date = "2026-11-03", title = "[EXEMPLE] la motion de censure d'exemple", kind = "MOC", adopted = false,
                counts = Counts(voters = 1, expressed = 1, inFavour = 1, against = 0, abstention = 0),
                positions = Positions(inFavour = listOf("an:PA900003")),
                url = "https://www.assemblee-nationale.fr/dyn/17/scrutins/103", dossier = "an-dossier:DLR5L17N90001",
            ),
        ),
    )

    fun json(pkg: MembersPackage): ByteArray = ParliamentFormat.json.encodeToString(MembersPackage.serializer(), pkg).encodeToByteArray()
    fun json(pkg: BallotsPackage): ByteArray = ParliamentFormat.json.encodeToString(BallotsPackage.serializer(), pkg).encodeToByteArray()

    fun ref(name: String, bytes: ByteArray) = PackageRef(name, ParliamentFormat.sha256Hex(bytes), bytes.size.toLong())

    fun index(packages: Map<String, ByteArray>, importedOn: String = "2026-11-07", example: Boolean = true) = ParliamentIndex(
        example = example,
        importedOn = importedOn,
        sources = listOf(
            DataSource("an", "Assemblée nationale, données ouvertes", "https://data.assemblee-nationale.fr/", "Licence Ouverte", importedOn),
            DataSource("senat", "Sénat, données ouvertes", "https://data.senat.fr/", "Licence Ouverte 2.0", importedOn),
            DataSource("pe", "Parlement européen, données ouvertes", "https://data.europarl.europa.eu/", "CC BY 4.0", importedOn),
        ),
        packages = packages.map { (name, bytes) -> ref(name, bytes) }.sortedBy { it.name },
    )

    fun packages(): Map<String, ByteArray> = linkedMapOf(
        ParliamentFormat.MEMBERS_PACKAGE to json(members),
        "scrutins-an-2026-10" to json(octobre),
        "scrutins-an-2026-11" to json(novembre),
    )

    fun catalog() = ParliamentCatalog(members, listOf(octobre, novembre))

    /** 2026-11-07 at noon, UTC. */
    const val NOW: Long = 1_794_052_800

    class Keys {
        val keys: IdentityKeys = IdentityKeys.generate()
        val signer = LocalEventSigner(keys)
        val trust: OpenDataTrust = OpenDataTrust.of(keys.publicKey.hex)

        suspend fun sign(index: ParliamentIndex, createdAt: Long = NOW): SignedEvent =
            signer.sign(ParliamentFormat.indexEvent(keys.publicKey, index, createdAt))
    }

    /** A server serving one index event and its packages; [online] false makes it unreachable. */
    class Server(var indexEvent: SignedEvent?, var packages: Map<String, ByteArray>) : RemoteEvents {
        var online = true
        val requests = mutableListOf<String?>()

        override suspend fun publish(event: SignedEvent): PublicationResult = PublicationResult.Unavailable(null)
        override suspend fun fetch(filter: RemoteFilter): FetchResult = FetchResult.Unavailable(null)

        override suspend fun fetchParliament(name: String?, etag: String?): OpenDataDownload {
            requests += name
            if (!online) return OpenDataDownload.Unavailable(null)
            if (name == null) return indexEvent?.let { OpenDataDownload.Body(it.toJson().encodeToByteArray()) } ?: OpenDataDownload.Unavailable(null)
            return packages[name]?.let { OpenDataDownload.Body(it) } ?: OpenDataDownload.UnknownPackage
        }
    }
}
