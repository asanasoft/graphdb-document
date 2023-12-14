package com.asanasoft.util

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

open class GenericMapSerializer : KSerializer<Map<String, Any?>> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GenericMapSerializer")

    override fun serialize(encoder: Encoder, value: Map<String, Any?>) {
        val jsonObject = JsonObject(value.mapValues { it.value.toJsonElement()})
        val serializer = encoder.serializersModule.serializer<JsonObject>()

        serializer.serialize(encoder, jsonObject)
    }

    override fun deserialize(decoder: Decoder): Map<String, Any?> {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("Can only deserialize Json content to generic Map")
        val rootElement = jsonDecoder.decodeJsonElement()
        return if (rootElement is JsonObject) rootElement.toMap() else throw SerializationException("Cannot deserialize Json content to generic Map")
    }

    protected fun Any?.toJsonElement(): JsonElement = when(this) {
        null -> JsonNull
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Map<*, *> -> toJsonObject()
        is Iterable<*> -> toJsonArray()
        else -> throw SerializationException("Cannot serialize value type $this")
    }

    protected fun Map<*,*>.toJsonObject(): JsonObject = JsonObject(this.entries.associate { it.key.toString() to it.value.toJsonElement() })

    protected fun Iterable<*>.toJsonArray(): JsonArray = JsonArray(this.map { it.toJsonElement() })

    protected fun JsonElement.toAnyNullableValue(): Any? = when (this) {
        is JsonPrimitive -> toScalarOrNull()
        is JsonObject -> toMap()
        is JsonArray -> toList()
    }

    protected fun JsonObject.toMap(): Map<String, Any?> = entries.associate {
        when (val jsonElement = it.value) {
            is JsonPrimitive -> it.key to jsonElement.toScalarOrNull()
            is JsonObject -> it.key to jsonElement.toMap()
            is JsonArray -> it.key to jsonElement.toAnyNullableValueList()
        }
    }

    protected fun JsonPrimitive.toScalarOrNull(): Any? = when {
        this is JsonNull -> null
        this.isString -> this.content
        else -> listOfNotNull(booleanOrNull, longOrNull, doubleOrNull).firstOrNull()
    }

    protected fun JsonArray.toAnyNullableValueList(): List<Any?> = this.map {
        it.toAnyNullableValue()
    }
}