package org.politisationpopulairefrancaise.core.signing

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** JSON form of an event's tags, as stored in the local database and on the server. */
object TagsJson {
    private val serializer = ListSerializer(ListSerializer(String.serializer()))

    fun encode(tags: List<List<String>>): String = Json.encodeToString(serializer, tags)

    fun decode(json: String): List<List<String>> = Json.decodeFromString(serializer, json)
}
