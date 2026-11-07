package org.politisationpopulairefrancaise.core.elus

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.politisationpopulairefrancaise.core.publication.ElusDownload
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * Fictitious packages of the directory of elected officials, in the format of the server (contract,
 * 1.4). The geography is real (communes, INSEE and postal codes of the Rhône and the Finistère), the
 * people are not: every name is "[EXEMPLE] Prénom Nom", visibly invented.
 */
internal object ElusFixtures {

    fun official(
        mandate: String,
        territory: String,
        name: String,
        function: String? = null,
        commune: String? = null,
        since: String = "2026-03-22",
        functionSince: String? = null,
    ): JsonObject = buildJsonObject {
        put("mandate", mandate)
        put("territory", territory)
        if (commune != null) put("commune", commune)
        put("name", "[EXEMPLE] $name")
        put("function", function?.let(::JsonPrimitive) ?: JsonNull)
        put("since", since)
        put("functionSince", functionSince?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun territory(code: String, name: String) = buildJsonObject {
        put("code", code)
        put("name", name)
    }

    private fun strings(vararg values: String) = JsonArray(values.map(::JsonPrimitive))

    private fun commune(code: String, name: String, postalCodes: List<String>, epci: String?, cantons: List<String>, circonscriptions: List<String>) = buildJsonObject {
        put("code", code)
        put("name", name)
        put("postalCodes", strings(*postalCodes.toTypedArray()))
        put("epci", epci?.let(::JsonPrimitive) ?: JsonNull)
        put("cantons", strings(*cantons.toTypedArray()))
        put("circonscriptions", strings(*circonscriptions.toTypedArray()))
    }

    fun pkg(
        code: String,
        name: String,
        region: JsonObject?,
        communes: List<JsonObject>,
        epcis: List<JsonObject> = emptyList(),
        cantons: List<JsonObject> = emptyList(),
        circonscriptions: List<JsonObject> = emptyList(),
        officials: List<JsonObject>,
    ): JsonObject = buildJsonObject {
        put("format", 1)
        put("example", true)
        put("code", code)
        put("name", name)
        put("region", region ?: JsonNull)
        put("sourceUpdatedOn", "2026-10-01")
        put("communes", JsonArray(communes))
        put("epcis", JsonArray(epcis))
        put("cantons", JsonArray(cantons))
        put("circonscriptions", JsonArray(circonscriptions))
        put("officials", JsonArray(officials))
    }

    /** The Rhône: Lyon (two constituencies, no canton), two communes sharing 69250, Villefranche (one canton). */
    val rhone: JsonObject = pkg(
        code = "69",
        name = "Rhône",
        region = territory("84", "Auvergne-Rhône-Alpes"),
        communes = listOf(
            commune("69003", "Albigny-sur-Saône", listOf("69250"), "200046977", emptyList(), listOf("6907")),
            commune("69123", "Lyon", listOf("69001", "69002", "69003"), "200046977", emptyList(), listOf("6901", "6902")),
            commune("69143", "Neuville-sur-Saône", listOf("69250"), "200046977", emptyList(), listOf("6907")),
            commune("69264", "Villefranche-sur-Saône", listOf("69400"), "200040590", listOf("6920"), listOf("6909")),
        ),
        epcis = listOf(territory("200040590", "Communauté d'agglomération Villefranche Beaujolais Saône"), territory("200046977", "Métropole de Lyon")),
        cantons = listOf(territory("6920", "Villefranche-sur-Saône")),
        circonscriptions = listOf(
            territory("6901", "1re circonscription du Rhône"),
            territory("6902", "2e circonscription du Rhône"),
            territory("6907", "7e circonscription du Rhône"),
            territory("6909", "9e circonscription du Rhône"),
        ),
        officials = listOf(
            // Listed in an order that is not the order of the council, to check the sorting.
            official("municipal", "69123", "Alix Conseil"),
            official("municipal", "69123", "Sacha Second", function = "2e adjoint", functionSince = "2026-03-28"),
            official("municipal", "69123", "Lou Premier", function = "1er adjoint", functionSince = "2026-03-28"),
            official("municipal", "69123", "Noa Maire", function = "Maire", functionSince = "2026-03-28"),
            official("municipal", "69123", "Eden Conseil"),
            official("municipal", "69003", "Camille Albigny", function = "Maire"),
            official("municipal", "69143", "Charlie Neuville", function = "Maire"),
            official("municipal", "69264", "Robin Villefranche", function = "Maire"),
            official("communautaire", "200046977", "Ange Metropole", function = "Président", commune = "69266"),
            official("communautaire", "200046977", "Yael Lyon", commune = "69123"),
            official("communautaire", "200046977", "Maxime Neuville", commune = "69143"),
            official("communautaire", "200040590", "Jo Agglo", function = "Président", commune = "69264"),
            official("departemental", "6920", "Ariel Canton"),
            official("departemental", "6920", "Gabi Departement", function = "Président du conseil départemental"),
            official("regional", "84", "Lou Region"),
            official("regional", "84", "Mael Vice", function = "1er vice-président"),
            official("regional", "84", "Sasha Presidence", function = "Président du conseil régional"),
            official("depute", "6901", "Elie Premiere"),
            official("depute", "6902", "Remi Deuxieme"),
            official("depute", "6907", "Tal Septieme"),
            official("depute", "6909", "Ilan Neuvieme"),
            official("senateur", "69", "Kim Senat"),
            official("senateur", "69", "Nour Senat"),
        ),
    )

    /** The Finistère: the Île-de-Sein belongs to no intercommunality. */
    val finistere: JsonObject = pkg(
        code = "29",
        name = "Finistère",
        region = territory("53", "Bretagne"),
        communes = listOf(
            commune("29003", "Audierne", listOf("29770"), "200067973", listOf("2911"), listOf("2907")),
            commune("29083", "Île-de-Sein", listOf("29990"), null, listOf("2911"), listOf("2907")),
        ),
        epcis = listOf(territory("200067973", "Communauté de communes Cap-Sizun - Pointe du Raz")),
        cantons = listOf(territory("2911", "Douarnenez")),
        circonscriptions = listOf(territory("2907", "7e circonscription du Finistère")),
        officials = listOf(
            official("municipal", "29083", "Morgan Sein", function = "Maire"),
            official("departemental", "2911", "Erwan Canton"),
            official("depute", "2907", "Gwen Depute"),
            official("senateur", "29", "Loane Senat"),
        ),
    )

    /** The members of the European Parliament elected in France. */
    val france: JsonObject = pkg(
        code = "FR",
        name = "France",
        region = null,
        communes = emptyList(),
        officials = listOf(
            official("europeen", "FR", "Ana Europe", since = "2024-07-16"),
            official("europeen", "FR", "Teo Europe", since = "2024-07-16"),
        ),
    )

    fun text(element: JsonElement): String = element.toString()

    /** The index announcing [packages] with the fingerprint and size of their exact text. */
    fun index(packages: Map<String, Pair<String, List<String>>>, texts: Map<String, String>): String = buildJsonObject {
        put("format", 1)
        put("example", true)
        put(
            "source",
            buildJsonObject {
                put("name", "Répertoire national des élus (ministère de l'Intérieur)")
                put("url", "https://www.data.gouv.fr/fr/datasets/5c34c4d1634f4173183a64f1/")
                put("licence", "Licence Ouverte 2.0")
                put("updatedOn", "2026-10-01")
            },
        )
        put("importedOn", "2026-11-07")
        put(
            "packages",
            buildJsonArray {
                packages.forEach { (code, value) ->
                    val bytes = texts.getValue(code).encodeToByteArray()
                    add(
                        buildJsonObject {
                            put("code", code)
                            put("name", value.first)
                            put("sha256", ElusDigest.sha256Hex(bytes))
                            put("bytes", bytes.size)
                            put("postalCodes", strings(*value.second.toTypedArray()))
                        },
                    )
                }
            },
        )
    }.toString()

    /** The texts of the three packages and the index announcing them. */
    fun server(): FakeElusServer {
        val texts = mapOf("69" to text(rhone), "29" to text(finistere), "FR" to text(france))
        val index = index(
            mapOf(
                "29" to ("Finistère" to listOf("29770", "29990")),
                "69" to ("Rhône" to listOf("69001", "69002", "69003", "69250", "69400")),
                "FR" to ("France" to emptyList()),
            ),
            texts,
        )
        return FakeElusServer(index, texts.toMutableMap())
    }
}

/** A server of the directory in memory: [online] switches it off, [requests] records what was asked. */
internal class FakeElusServer(var indexText: String, val packages: MutableMap<String, String>) : RemoteEvents {
    var online = true
    val requests = mutableListOf<String?>()
    val etags = mutableListOf<String?>()

    override suspend fun publish(event: SignedEvent): PublicationResult = PublicationResult.Accepted(event.id)

    override suspend fun fetch(filter: RemoteFilter): FetchResult = FetchResult.Events(emptyList())

    override suspend fun fetchElus(code: String?, etag: String?): ElusDownload {
        requests += code
        etags += etag
        if (!online) return ElusDownload.Unavailable()
        if (code == null) {
            val bytes = indexText.encodeToByteArray()
            return if (etag == ElusDigest.sha256Hex(bytes)) ElusDownload.NotModified else ElusDownload.Body(bytes)
        }
        val text = packages[code] ?: return ElusDownload.UnknownPackage
        return ElusDownload.Body(text.encodeToByteArray())
    }
}
