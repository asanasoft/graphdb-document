package com.asanasoft.util

import com.asanasoft.graphdb.GraphEntity
import kotlinx.serialization.json.*

data class Result<out T>(
    val value       : T? = null,
    val cause       : Throwable? = null,
    val isSuccess   : Boolean = (value != null),
    val isFailure   : Boolean = (cause != null),
    val message     : String = ""
)

fun noop() = Unit
fun noop(someValue : Any?) = Unit
val pass : Unit = Unit

val mapSerializer = GenericMapSerializer()

fun jsonObjectToMap(json : JsonElement, map : MutableMap<String, Any?>) {
    val jsonObject = json as JsonObject
    for (key in jsonObject.keys) {
        var jsonElement = jsonObject[key]

        when (jsonElement) {
            is JsonPrimitive -> map.put(key, jsonElement.content)
            is JsonObject -> map.put(key, GraphEntity(jsonElement))
            is JsonArray -> {
                val list = mutableListOf<GraphEntity>()

                for (elem in jsonElement.iterator()) {
                    list.add(GraphEntity(elem as JsonObject))
                }

                map.put(key, list)
            }
            null -> TODO()
        }
    }
}

fun stringToJsonElement(jsonString : String) : JsonElement {
    Json.parseToJsonElement(jsonString).let {
        return when (it) {
            is JsonPrimitive -> it
            is JsonObject -> it
            is JsonArray -> it
            else -> JsonPrimitive("")
        }
    }
}

fun jsonElementToString(json : JsonElement) : String {
    return when (json) {
        is JsonPrimitive -> json.content
        is JsonObject -> json.toString()
        is JsonArray -> json.toString()
        else -> ""
    }
}

fun jsonElementToMap(json : JsonElement) : Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    jsonObjectToMap(json, map)
    return map
}

fun mapToJsonElement(map : Map<String, Any?>) : JsonElement {
    return Json.encodeToJsonElement(mapSerializer, map)
}

fun mapToJsonElement(map : Map<String, String>, key : String) : JsonElement {
    return Json.encodeToJsonElement(mapSerializer, mapOf(key to map))
}
