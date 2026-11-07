package org.politisationpopulairefrancaise.ui.comprendre.elus

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.politisationpopulairefrancaise.core.elus.ElusDigest
import org.politisationpopulairefrancaise.core.publication.ElusDownload
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import java.util.Collections

/**
 * A server with the directory of elected officials in the format of the contract (1.4): fictitious
 * people ("[EXEMPLE] Prénom Nom", visibly invented) in the real geography of the Rhône. No content
 * is served ([fetch] answers an empty list). [online] switches it off, [tampered] serves another
 * package than the one the index announces, [requests] records the codes asked (never a postal code).
 */
internal class ElusTestServer : RemoteEvents {
    @Volatile var online = true
    @Volatile var tampered = false
    val requests: MutableList<String?> = Collections.synchronizedList(mutableListOf())

    val packages: Map<String, String> = mapOf("69" to rhone().toString(), "FR" to france().toString())

    val indexText: String = buildJsonObject {
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
                listOf(Triple("69", "Rhône", listOf("69001", "69002", "69003", "69250")), Triple("FR", "France", emptyList())).forEach { (code, name, postalCodes) ->
                    val bytes = packages.getValue(code).encodeToByteArray()
                    add(
                        buildJsonObject {
                            put("code", code)
                            put("name", name)
                            put("sha256", ElusDigest.sha256Hex(bytes))
                            put("bytes", bytes.size)
                            put("postalCodes", JsonArray(postalCodes.map(::JsonPrimitive)))
                        },
                    )
                }
            },
        )
    }.toString()

    override suspend fun publish(event: SignedEvent): PublicationResult = PublicationResult.Accepted(event.id)

    override suspend fun fetch(filter: RemoteFilter): FetchResult = FetchResult.Events(emptyList())

    override suspend fun fetchElus(code: String?, etag: String?): ElusDownload {
        requests += code
        if (!online) return ElusDownload.Unavailable()
        if (code == null) return ElusDownload.Body(indexText.encodeToByteArray())
        val text = packages[code] ?: return ElusDownload.UnknownPackage
        return ElusDownload.Body((if (tampered) text.replace("Noa Maire", "Noa Autre") else text).encodeToByteArray())
    }

    private companion object {
        fun official(mandate: String, territory: String, name: String, function: String? = null, commune: String? = null) = buildJsonObject {
            put("mandate", mandate)
            put("territory", territory)
            if (commune != null) put("commune", commune)
            put("name", "[EXEMPLE] $name")
            put("function", function?.let(::JsonPrimitive) ?: JsonNull)
            put("since", "2026-03-22")
            put("functionSince", function?.let { JsonPrimitive("2026-03-28") } ?: JsonNull)
        }

        fun territory(code: String, name: String) = buildJsonObject {
            put("code", code)
            put("name", name)
        }

        fun commune(code: String, name: String, postalCodes: List<String>, circonscription: String) = buildJsonObject {
            put("code", code)
            put("name", name)
            put("postalCodes", JsonArray(postalCodes.map(::JsonPrimitive)))
            put("epci", "200046977")
            put("cantons", JsonArray(emptyList()))
            put("circonscriptions", JsonArray(listOf(JsonPrimitive(circonscription))))
        }

        fun pkg(code: String, name: String, region: JsonObject?, communes: List<JsonObject>, officials: List<JsonObject>, extra: Map<String, List<JsonObject>> = emptyMap()) = buildJsonObject {
            put("format", 1)
            put("example", true)
            put("code", code)
            put("name", name)
            put("region", region ?: JsonNull)
            put("sourceUpdatedOn", "2026-10-01")
            put("communes", JsonArray(communes))
            listOf("epcis", "cantons", "circonscriptions").forEach { put(it, JsonArray(extra[it].orEmpty())) }
            put("officials", JsonArray(officials))
        }

        fun rhone() = pkg(
            code = "69",
            name = "Rhône",
            region = territory("84", "Auvergne-Rhône-Alpes"),
            communes = listOf(
                commune("69003", "Albigny-sur-Saône", listOf("69250"), "6907"),
                commune("69123", "Lyon", listOf("69001", "69002", "69003"), "6901"),
                commune("69143", "Neuville-sur-Saône", listOf("69250"), "6907"),
            ),
            extra = mapOf(
                "epcis" to listOf(territory("200046977", "Métropole de Lyon")),
                "circonscriptions" to listOf(territory("6901", "1re circonscription du Rhône"), territory("6907", "7e circonscription du Rhône")),
            ),
            officials = buildList {
                add(official("municipal", "69123", "Noa Maire", "Maire"))
                add(official("municipal", "69123", "Lou Premier", "1er adjoint"))
                // A large council, to show the first twelve and the others on request.
                (1..20).forEach { add(official("municipal", "69123", "Membre Conseil$it")) }
                add(official("municipal", "69003", "Camille Albigny", "Maire"))
                add(official("municipal", "69143", "Charlie Neuville", "Maire"))
                add(official("communautaire", "200046977", "Ange Metropole", "Président", commune = "69266"))
                add(official("communautaire", "200046977", "Maxime Neuville", commune = "69143"))
                add(official("departemental", "6920", "Gabi Departement", "Président du conseil départemental"))
                add(official("regional", "84", "Sasha Presidence", "Président du conseil régional"))
                add(official("regional", "84", "Lou Region"))
                add(official("depute", "6901", "Elie Premiere"))
                add(official("depute", "6907", "Tal Septieme"))
                add(official("senateur", "69", "Kim Senat"))
            },
        )

        fun france() = pkg(
            code = "FR",
            name = "France",
            region = null,
            communes = emptyList(),
            officials = listOf(official("europeen", "FR", "Ana Europe"), official("europeen", "FR", "Teo Europe")),
        )
    }
}
