package org.politisationpopulairefrancaise.tools.opendata

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.Group
import org.politisationpopulairefrancaise.core.voir.parlement.Member
import java.io.InputStream

/*
 * The Sénat (`senateurs.json`, Licence Ouverte 2.0): the senators in office and their groups. The
 * nominal votes of the Sénat are not published as data files (first version: link only). Never
 * read: civility, feminine form, professional category, photograph, social networks, seat, bodies.
 */
object Senat {
    const val SITE: String = "https://www.senat.fr"
    /** Where a senator without a readable page of their own is sent: the list of senators. */
    const val LIST_PAGE: String = "$SITE/senateurs/senatl.html"

    private val MATRICULE = Regex("^[0-9A-Z]{4,12}$")
    private val GROUP_CODE = Regex("^[0-9A-Za-z_-]{1,20}$")
    private val DEPARTMENT = Regex("^[0-9A-Z]{2,3}$")

    /** The file is one list; read whole (400 kB), under [SourceEntry.MAX_ENTRY_BYTES]. */
    fun members(stream: InputStream): Roster {
        val text = stream.use { readBounded(it, SourceEntry.MAX_ENTRY_BYTES, "senateurs.json") }.decodeToString()
        val root = try {
            lenientJson.parseToJsonElement(text)
        } catch (e: Exception) {
            throw UnreadableSourceException("senateurs.json: not a JSON document")
        }
        val list = when (root) {
            is JsonArray -> root
            is JsonObject -> root.values.filterIsInstance<JsonArray>().firstOrNull() ?: throw UnreadableSourceException("senateurs.json: no list")
            else -> throw UnreadableSourceException("senateurs.json: no list")
        }
        val groups = HashMap<String, Group>()
        val members = list.map { senator ->
            val matricule = senator["matricule"].text()?.uppercase()?.takeIf(MATRICULE::matches) ?: throw UnreadableSourceException("senateurs.json: no matricule")
            val first = senator["prenom"].text()
            val last = senator["nom"].text()
            if (first == null || last == null) throw UnreadableSourceException("senateurs.json: no name for a senator")
            val group = senator["groupe"]
            val groupId = group["code"].text()?.takeIf(GROUP_CODE::matches)?.let { code ->
                "senat:groupe:$code".also { id ->
                    val label = group["libelle"].text() ?: throw UnreadableSourceException("senateurs.json: group without label")
                    groups.getOrPut(id) { Group(id = id, chamber = Chamber.SENAT, label = label, abbreviation = group["libelleCourt"].text()) }
                }
            }
            val place = senator["circonscription"]
            val raw = place["code"].text()?.uppercase() ?: throw UnreadableSourceException("senateurs.json: no constituency")
            val department = (if (raw.length == 1 && raw[0].isDigit()) "0$raw" else raw).takeIf(DEPARTMENT::matches)
                ?: throw UnreadableSourceException("senateurs.json: unreadable constituency")
            Member(
                id = "senat:$matricule",
                name = "$first $last",
                chamber = Chamber.SENAT,
                group = groupId,
                territory = department,
                territoryName = place["libelle"].text(),
                url = pageOf(senator["url"].text()),
            )
        }
        if (members.isEmpty()) throw UnreadableSourceException("senateurs.json: no senator")
        return Roster(members.sortedBy { it.id }, groups.values.sortedBy { it.id })
    }

    /** Only a page of www.senat.fr, in HTTPS; anything else becomes the list of senators. */
    internal fun pageOf(url: String?): String {
        val https = url?.replaceFirst(Regex("^http://"), "https://") ?: return LIST_PAGE
        return https.takeIf { it.startsWith("$SITE/") && it.none { c -> c.isWhitespace() || c == '"' || c == '<' || c == '>' } } ?: LIST_PAGE
    }
}
