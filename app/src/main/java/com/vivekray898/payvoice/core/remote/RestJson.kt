package com.vivekray898.payvoice.core.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Small PostgREST/JSON helpers for the Supabase REST layer. PostgREST
 * * serializes timestamptz as ISO-8601 by default; this schema deliberately
 * uses epoch-millis bigint columns so the wire format stays trivially parseable
 * without date deserializers.
 */
object RestJson {

    val json = Json { ignoreUnknownKeys = true }

    /** Safe string read from a JSON object. */
    fun str(obj: JsonObject, key: String): String? =
        (obj[key] as? JsonPrimitive)?.let { p -> if (p.isString) p.content else null }

    /** Safe long read (JSON number or numeric string). */
    fun long(obj: JsonObject, key: String): Long? =
        (obj[key] as? JsonPrimitive)?.let { it.content.toLongOrNull() ?: it.longOrNull }

    /** Safe boolean read (JSON boolean or "true"/"false" string). */
    fun bool(obj: JsonObject, key: String): Boolean? =
        (obj[key] as? JsonPrimitive)?.let { p ->
            when (p.content) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }

    /** Parse a PostgREST array response body; empty on malformed input. */
    fun parseArray(body: String): List<JsonObject> = runCatching {
        (json.parseToJsonElement(body) as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?: emptyList()
    }.getOrDefault(emptyList())
}
