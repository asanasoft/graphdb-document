package com.asanasoft.graphdb

import com.asanasoft.util.*
import com.asanasoft.util.DBConstants.INTERNAL_CLASS
import com.asanasoft.util.DBConstants.INTERNAL_ID
import com.asanasoft.util.DBConstants.USE_KEY
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.shaded.jackson.annotation.ObjectIdGenerators.UUIDGenerator

/**
 * Base class for all things Entity. This way, we don't have to keep writing boilerplate code...
 *
 */
open class GraphEntity : MutableMap<String, Any?> {
    var clazz: String? = null //class of the GraphEntity
        private set
    var _key: String? = null //The field to use as a key
        private set
    var _id: String? = null //The id of the Entity
        private set
    private val DEFAULT_CLASS_NAME = "GraphEntity"
    private val DEFAULT_KEY: String = INTERNAL_ID

    private var delegate: MutableMap<String, Any?> = mutableMapOf()

    protected val graphDBService: GraphDBService?
        /**
         * I never make my methods private. Principles...
         */
        protected get() = null

    constructor() {
        setType(DEFAULT_CLASS_NAME, DEFAULT_KEY, null)
    }

    constructor(className: String, keyFieldToUse: String, id: String) {
        setType(className, keyFieldToUse, id)
        delegate[_key!!] = id
    }

    constructor(className: String, keyFieldToUse: String, id: String, json: String) {
        val jsonObject = stringToJsonElement(json!!)
        jsonObjectToMap(jsonObject, delegate)
        setType(className, keyFieldToUse, id)
    }

    constructor(json: String) : this(stringToJsonElement(json) as JsonObject) {
    }

    constructor(vertex: Vertex) {
        setType(DEFAULT_CLASS_NAME, DEFAULT_KEY, null)
        for (key in vertex.keys()) {
            delegate.put(key, vertex.property<String>(key).value())
        }
    }

    constructor(jsonObject : JsonObject) {
        delegate = jsonElementToMap(jsonObject).toMutableMap()

        with (delegate) {
            clazz = if (get(INTERNAL_CLASS) != null) {
                get(INTERNAL_CLASS).toString()
            } else {
                DEFAULT_CLASS_NAME
            }

            _key = if (get(USE_KEY) != null) {
                get(USE_KEY).toString()
            } else {
                DEFAULT_KEY
            }

            _id = if (get(INTERNAL_ID) != null) {
                get(INTERNAL_ID).toString()
            } else {
                UUIDGenerator().generateId(this).toString()
            }
        }

        delegate[INTERNAL_CLASS]    = clazz!!
        delegate[USE_KEY]           = _key!!
        delegate[INTERNAL_ID]       = _id!!
    }

    protected open fun setType(className: String? = DEFAULT_CLASS_NAME, keyField: String? = DEFAULT_KEY, id: String?) {
        clazz = className
        _key = keyField
        _id = if (delegate[_key] != null) delegate[_key].toString() else  UUIDGenerator().generateId(this).toString()

        /**
         * Said boilerplate code...
         */
        delegate[INTERNAL_CLASS]    = clazz!!
        delegate[USE_KEY]           = _key!!
        delegate[INTERNAL_ID]       = _id!!
    }

    fun getString(key: String): String? {
        var result: String? = null

        if (delegate.containsKey(key)) {
            result = delegate.get(key).toString()
        }

        return result
    }

    override val entries: MutableSet<MutableMap.MutableEntry<String, Any?>>
        get() = delegate.entries

    override val keys: MutableSet<String>
        get() = delegate.keys

    override val size: Int
        get() = delegate.size

    override val values: MutableCollection<Any?>
        get() = delegate.values

    override fun clear() {
        delegate.clear()
    }

    override fun containsValue(value: Any?): Boolean {
        return delegate.containsValue(value)
    }

    override fun containsKey(key: String): Boolean {
        return delegate.containsKey(key)
    }

    override fun equals(o: Any?): Boolean {
        var result = false
        if (o is GraphEntity) {
            val other = o
            result =
                if ((_id != null || get(USE_KEY) != null) && (other._id != null || other[USE_KEY] != null)) {
                    get(USE_KEY) == other[USE_KEY]
                } else {
                    val o1: MutableMap<String, Any?> = this
                    val o2: MutableMap<String, Any?> = other
                    
                    o1.remove(INTERNAL_CLASS)
                    o1.remove(INTERNAL_ID)
                    o1.remove(USE_KEY)
                    o2.remove(INTERNAL_CLASS)
                    o2.remove(INTERNAL_ID)
                    o2.remove(USE_KEY)
                    o1 == o2
                }
        }
        return result
    }

    override fun isEmpty(): Boolean {
        return delegate.isEmpty()
    }

    override fun remove(key: String): Any? {
        return delegate.remove(key)
    }

    override fun putAll(from: Map<out String, Any?>) {
        delegate.putAll(from)
    }

    override fun put(key: String, value: Any?): Any? {
        return delegate.put(key, value)
    }

    override fun get(key: String): Any? {
        return delegate.get(key)
    }

    /**
     * Let's make this class behave like an Object DB by giving the objects
     * CRUD features...
     *
     * These methods are in the react pattern, since AbstractGraphDBService is reactive...
     */
    /**
     * Fetch the previously persisted version of this object or a newly created one.
     */
    suspend fun fetch(): Result<GraphEntity> {
        var id = this._id
        var result: Result<GraphEntity?>

        if (id == null) {
            result = Result(graphDBService!!.retrieveVertexByKey(_key!!, delegate[_key].toString()).value?.get(0))
        } else {
            result = graphDBService!!.retrieveVertex(id)
        }

        return result
    }

    suspend fun remove() {
        graphDBService!!.delete(this)
    }

    suspend fun store() {
        graphDBService!!.insertOrUpdateVertex(clazz!!, this)
    }

    suspend fun update() {
        graphDBService!!.updateVertex(clazz!!, this)
    }

    /**
     * We'll use the "flow pattern" for our next set of methods...
     */

    /**
     * A flow-ified synonym for 'put(String, Object)'
     * @param key
     * @param value
     * @return
     */
    open fun add(key: String, value: Any?): GraphEntity {
        delegate[key] = value.toString()
        return this
    }

    open fun addChild(newObject: GraphEntity): GraphEntity {
        /**
         * Let's do something cool here...
         *
         * If this is the first time this _class is added, then we'll
         * make it a field of this JSON
         * else, put it in an array
         */
        if (delegate.containsKey(newObject.clazz!!)) {
            val tempObj = delegate.getValue(newObject.clazz!!)
            if (tempObj is List<*>) {
                (tempObj as MutableList<GraphEntity>).add(newObject)
            } else {
                val jsonArray = mutableListOf<GraphEntity>()
                jsonArray.add(tempObj as GraphEntity)
                jsonArray.add(newObject)
                delegate[newObject.clazz!!] = jsonArray
            }
        } else {
            delegate[newObject.clazz!!] = newObject
        }

        return this
    }

    open fun removeChild(oldObject: GraphEntity): GraphEntity {
        if (delegate.containsKey(oldObject.clazz!!)) {
            val tempObj = delegate.getValue(oldObject.clazz!!)
            if (tempObj is List<*> && (tempObj as MutableList<*>).contains(oldObject)) {
                tempObj.remove(oldObject)
            } else {
                delegate.remove(oldObject.clazz)
            }
        }

        return this
    }

    override fun toString(): String {
        var result = "{}"

        result = mapToJsonElement(delegate.toMap()).toString()

        return result
    }
}
