package com.asanasoft.graphdb.impl

import com.asanasoft.util.DBConstants
import com.asanasoft.graphdb.GraphDBService
import com.asanasoft.graphdb.GraphEntity
import com.asanasoft.util.Result
import org.apache.tinkerpop.gremlin.groovy.jsr223.GremlinGroovyScriptEngine
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.structure.Graph
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

abstract class AbstractGraphDBService : GraphDBService {
    protected var logger = LoggerFactory.getLogger(AbstractGraphDBService::class.java)
    private var graphDB: Graph? = null
    private var g: GraphTraversalSource? = null
    protected val scriptEngine = GremlinGroovyScriptEngine()

    fun setGraphDB(newGraphDB: Graph?) {
        graphDB = newGraphDB
        g = graphDB!!.traversal()
    }

    override suspend fun gremlinScript(params: Map<String, String>, script: String): Result<List<GraphEntity>> {
        return gremlinScriptBlocking(params, script, true)
    }

    override suspend fun gremlinScriptShallow(params: Map<String, String>, script: String): Result<List<GraphEntity>> {
        return gremlinScriptBlocking(params, script, false)
    }

    protected open fun gremlinScriptBlocking(
        params: Map<String, String>,
        script: String,
        getChildren: Boolean
    ): Result<List<GraphEntity>> {
        val result = Result<List<GraphEntity>>()
        val listResult: MutableList<GraphEntity> = ArrayList()
        try {
            val bindings = scriptEngine.createBindings()
            bindings["g"] = g
            bindings["result"] = listResult
            bindings.putAll(params)
            logger.debug("About to execute: $script")
            val scriptObject = scriptEngine.eval(script, bindings)
            logger.trace("Script Engine evaluated script - $script")
            val scriptResult = scriptObject as GraphTraversal<*, *>
            var vertex: Vertex
            var graphObject: Any
            logger.trace("hasNext() = " + scriptResult.hasNext())
            while (scriptResult.hasNext()) {
                graphObject = scriptResult.next()
                logger.trace("graphObjet = " + graphObject.javaClass.getName())

                //Only aggregate Vertices
                if (graphObject is Vertex) {
                    logger.trace("Retrieving Vertex Graph...")
                    vertex = graphObject
                    listResult.add(retrieveVertexGraph(vertex, getChildren, 0))
                }
            }
            graphDB!!.tx().commit()
        } catch (e: Exception) {
            graphDB!!.tx().rollback()
            logger.error("An error executing: $script", e)
        }
        return result
    }

    override suspend fun insertVertex(clazz: String, vertex: GraphEntity): Result<Boolean> {
        var result: Result<Boolean> = Result(false)

        getGraphEntityFrom(createOrUpdateVertex(clazz, vertex))?.let {
            result = Result(true)
        }

        return result
    }

    /**
     * createOrUpdateVertexBlocking takes in a complex GraphEntity and persists it using a bottomw-up
     * approach...
     * Here we assume that vertex is a completely marshalled JsonObject.
     *
     * @param clazz - the class name to assign this vertex
     * @param vertex - the Json representation of the vertex
     * @return a Vertex object
     */
    protected open suspend fun createOrUpdateVertex(clazz: String, vertex: GraphEntity): Vertex? {
        var vertexObj: Vertex? = null

        try {
            logger.debug("In createOrUpdateVertexBlocking...")
            /**
             * The "id" passed into addVertex may be ignored. OrientDB will use this information as the className
             * of the Vertex instance. Bitsy ignores it...
             */

            /**
             * This seq# is for child objects in the vertex object that appear in an entityList. This will keep the
             * entry order of the objects...
             */
            val seq = AtomicInteger(0)

            //First, determine if this is an update or a new vertex...
            val vertexId = vertex[DBConstants.INTERNAL_ID].toString()
            var updateVertex = !vertexId.isEmpty()
            if (updateVertex) {
                //...this is an update, so look up the vertex...
                logger.trace("Looking up Vertex:{}", vertex)

                try {
                    vertexObj = g!!.V().has(
                        DBConstants.INTERNAL_ID,
                        vertex[DBConstants.INTERNAL_ID].toString()
                    ).next()
                    logger.debug("Updating Vertex with id =" + vertexObj.id().toString())
                } catch (e: NoSuchElementException) {
                    /**
                     * ...if it doesn't exist,...
                     */
                    updateVertex = false
                }
            }
            /**
             * ...make a second attempt at finding the vertex...
             */
            if (!updateVertex && vertex[DBConstants.USE_KEY] != null) {
                /**
                 * This GraphEntity can be a hand-coded reference to an existing object,
                 * if so, look up the object using the field indicated by DBConstants.USE_KEY...
                 */
                val idKey = vertex[DBConstants.USE_KEY].toString()
                try {
                    vertexObj = g!!.V().has(idKey, vertex[idKey].toString()).next()
                    updateVertex = true
                } catch (e: NoSuchElementException) {
                    /**
                     * ...if it doesn't exist, create it...
                     */
                }
            }
            logger.trace("updateVertex = $updateVertex")
            if (!updateVertex) {
                //...this is a new vertex, so create one...
                vertexObj = graphDB!!.addVertex(clazz)
                logger.debug("Vertex inserted. Updating properties...")
            }
            val vertexClass = vertex.getString(DBConstants.INTERNAL_CLASS)
            for (key in vertex.keys) {
                /**
                 * If the current property is NOT a JsonObject or a entityList, then add the key/value entry...
                 * (no need to check whether or not it's an update)
                 */
                if (vertex[key] !is List<*> && vertex[key] !is GraphEntity) {
                    logger.trace("key / value = " + key + "/" + Objects.requireNonNull(vertex[key]).toString())
                    vertexObj!!.property(key, vertex[key])
                } else {
                    /**
                     * If the current property points to an entityList,...
                     */
                    if (vertex[key] is List<*>) {
                        logger.trace("Property $key is a List...")
                        /**
                         * ...the first thing we want to do is, if this is an update, then we need to reconcile the array.
                         * To do that, we do the following, for all linked vertices that are private to this vertex
                         * (i.e., don't have a clazz associated with them):
                         *
                         * 1) Get the current list of vertices that are linked to this vertex...
                         * 2) Delete all edges to these child vertices...
                         * 3) Loop through the current array...
                         * 4) "Mark" vertices that exists in both arrays...
                         * 5) Delete the vertices that no longer exist in the new array...
                         * 6) Add new vertices (if applicable)...
                         * 7) Re-Link with new edges.
                         */
                        val entityList = vertex[key] as List<*>?
                        var found : Boolean
                        if (updateVertex) {
                            var vertices: Iterator<Vertex>
                            vertices = g!!.V(vertexObj!!.id()).out("array:$key")
                            var currentVertex: GraphEntity? = null
                            while (vertices.hasNext()) {
                                currentVertex = GraphEntity(vertices.next())
                                val currentClass = currentVertex.getString(DBConstants.INTERNAL_CLASS)
                                found = false
                                var i = 0
                                while (i < entityList!!.size && !found) {
                                    found = currentVertex == entityList[i]
                                    i++
                                }
                                /**
                                 * The current object is NOT in the new array, so delete it from the system...
                                 */
                                if (!found && currentClass == "$vertexClass:$key") {
                                    currentVertex.remove()
                                }
                            }
                            /**
                             * Delete the current edges...
                             */
                            logger.debug("Deleting edges with label = array:$key")
                            g!!.V(vertexObj.id()).outE("array:$key").drop().iterate()
                        }
                        seq.set(0)
                        for (jsonObject in entityList!!) {
                            var childVertex: Vertex?

                            /**
                             * ...and create/update the Vertex object...
                             */
                            var newClass = (jsonObject as GraphEntity).getString(DBConstants.INTERNAL_CLASS)
                            var relationship = "relate"
                            if (newClass == null || newClass == "$clazz:$key") {
                                newClass = "$clazz:$key"
                                relationship = "child"
                            }
                            childVertex = createOrUpdateVertex(newClass, jsonObject)
                            /**
                             * ...then relate it to the parent Vertex via an edge with the type of relationship...
                             */
                            logger.trace("Relating child vertex with id = " + childVertex!!.id().toString())
                            vertexObj!!.addEdge(
                                "array:$key",
                                childVertex,
                                "type",
                                relationship,
                                "seq",
                                seq.incrementAndGet(),
                                "key",
                                key
                            )
                        }
                        /**
                         * ...then keep the original structure by creating a "property slot" where the array will be placed
                         * during unmarshalling.
                         */
                        vertexObj!!.property(key, "array:$key")
                    } else {
                        /**
                         * ...else, if the property points to an JsonObject...
                         */
                        val childObject = vertex[key] as GraphEntity
                        var childClazz = childObject.getString(DBConstants.INTERNAL_CLASS)
                        var relationship = "relate"

                        childClazz = "$clazz:$key"
                        relationship = "child"

                        val childVertex = createOrUpdateVertex(childClazz, childObject)
                        /**
                         * ...then relate it to the parent Vertex via an edge with the type "child" designation...
                         */
                        if (!updateVertex) {
                            /**
                             * create the edge if this is a new vertex...
                             *
                             * There's no need to remove the edge and then create it in the case of a "relate".
                             * We only do it with entityLists because the arrays between the stored and the updated
                             * may have different number of entries.
                             */
                            vertexObj!!.addEdge("object:$key", childVertex, "type", relationship)
                        }
                        /**
                         * ...then keep the original structure by creating a "property slot" where the object will be placed
                         * during unmarshalling.
                         */
                        vertexObj!!.property(key, "object:$key")
                    }
                }
            }

            /**
             * We're tracking our own ids and classNames. Some GraphDB implementations take care of these...
             */
            vertexObj!!.property(DBConstants.INTERNAL_ID, vertexObj.id().toString())
            if (!updateVertex) {
                vertexObj.property(DBConstants.INTERNAL_CLASS, clazz)
                if (vertex.getString(DBConstants.USE_KEY) == null) {
                    vertexObj.property(DBConstants.USE_KEY, DBConstants.INTERNAL_ID)
                }
            }
            /**
             * Finally, timestamp this vertex...
             */
            val sdf = SimpleDateFormat("yyyy-MM-DD HH:mm:ss")
            val timeStamp = sdf.format(System.currentTimeMillis())
            vertexObj.property("updatedOn", timeStamp)
            graphDB!!.tx().commit()
        } catch (e: Exception) {
            graphDB!!.tx().rollback()
            logger.error("An error occurred in createOrUpdateVertexblocking...", e)
        }
        return vertexObj
    }

    override suspend fun updateVertex(clazz: String, vertex: GraphEntity): Result<Boolean> {
        val vertexResult = createOrUpdateVertex(clazz, vertex)
        return Result(vertexResult != null)
    }

    override suspend fun insertOrUpdateVertex(clazz: String, vertex: GraphEntity): Result<Boolean> {
        val result: Result<Boolean> = updateVertex(clazz, vertex)
        return result
    }

    override suspend fun retrieveVertex(id: String): Result<GraphEntity> {
        var result: Result<GraphEntity>
        val ids = arrayOf(id)
        val interimResult = retrieveVerticesByIds(Arrays.asList(*ids), true)

        if (interimResult.value != null) {
            result = Result(interimResult.value!![0])
        } else {
            result = Result(null, interimResult.cause)
        }

        return result
    }

    override suspend fun retrieveVertex(id: String, getChildren: Boolean): Result<GraphEntity> {
        val ids = arrayOf(id)
        return Result(retrieveVertex(Arrays.asList(*ids), getChildren).value!![0])
    }

    open suspend fun retrieveVertex(ids: List<String>, getChildren: Boolean): Result<List<GraphEntity>> {
        return retrieveVerticesByIds(ids, getChildren)
    }

    override suspend fun retrieveVertex(visitor: GraphEntity): Result<GraphEntity> {
        val key = (visitor.getString(DBConstants.USE_KEY) ?: DBConstants.INTERNAL_ID)
        val value = visitor.getString(key)!!

        return Result(retrieveVertexByKey(key, value).value!![0])
    }

    override suspend fun retrieveVerticesByIds(ids: List<String>): Result<List<GraphEntity>> {
        return retrieveVerticesByIds(ids, true)
    }

    override suspend fun retrieveVerticesByIds(ids: List<String>, getChildren: Boolean): Result<List<GraphEntity>> {
        val result: MutableList<GraphEntity> = ArrayList()
        try {
            val vertices: Iterator<Vertex> = g!!.V(*ids.toTypedArray())
            while (vertices.hasNext()) {
                val retrievedVertex = retrieveVertexGraph(vertices.next(), getChildren, 0)
                result.add(retrievedVertex)
            }
        } catch (e: Exception) {
            logger.error("An error occurred in retrieveVertex:", e)
        }
        return Result(result)
    }

    override suspend fun retrieveVertexByKey(key: String, value: String): Result<List<GraphEntity>> {
        val resultList: MutableList<GraphEntity> = ArrayList()
        var result: Result<List<GraphEntity>>

        try {
            val vertices: Iterator<Vertex> = g!!.V().has(key, value)
            while (vertices.hasNext()) {
                resultList.add(retrieveVertexGraph(vertices.next()))
            }
            result = Result(resultList)
        } catch (e: Exception) {
            logger.error("An error occurred in retrieveVertex:", e)
            result = Result(null, e)
        }

        return result
    }

    /**
     * retrieveVertexGraph retrieves either a complex Vertex graph (if getChildren=true) or
     * a shallow Vertex graph (if getChildren=false), for the supplied starting Vertex 'startVertex'.
     * @param startVertex
     * @return
     */
    open protected fun retrieveVertexGraph(
        startVertex: Vertex,
        getChildren: Boolean = true,
        recursionLevel: Int = 0
    ): GraphEntity {
        if (recursionLevel == 0) {
            logger.debug("In retrieveVertexGraph, with getChildren = $getChildren, level=$recursionLevel...")
        }
        val result = getGraphEntityFrom(startVertex)
        var value: Any? = null

        for (key in result?.keys!!) {
            value = result?.get(key)
            logger.trace("value = $value")
            if (value is String && value.startsWith("object:")) {
                val vertices: Iterator<Vertex> = g!!.V(startVertex.id()).outE(value as String?).inV()
                if (getChildren) {
                    result[key] = retrieveVertexGraph(vertices.next(), true, recursionLevel + 1)
                } else {
                    val shallowObj = buildShallowCopyOfVertex(vertices.next())
                    result[key] = shallowObj
                }
            } else if (value is String && value.startsWith("array:")) {
                val entityList: MutableList<Any> = ArrayList()
                result[key] = entityList
                val vertices: Iterator<Vertex> = g!!.V(startVertex.id()).outE(value as String?).order().by("seq").inV()
                while (vertices.hasNext()) {
                    if (getChildren) {
                        entityList.add(retrieveVertexGraph(vertices.next(), true, recursionLevel + 1))
                    } else {
                        val shallowObj = buildShallowCopyOfVertex(vertices.next())
                        entityList.add(shallowObj)
                    }
                }
            }
        }

        return result
    }

    /**
     * buildShallowCopyOfVertex returns a GraphEntity that is a shallow copy of the supplied vertex. That is, it contains
     * only three of the vertex's fields: _class, _key, and the field named by _key.
     * @param v
     * @return
     */
    open protected fun buildShallowCopyOfVertex(v: Vertex?): GraphEntity {
        val obj = getGraphEntityFrom(v)
        val keyFieldName = obj!!.getString(DBConstants.USE_KEY)!!
        return GraphEntity(
            obj.getString(DBConstants.INTERNAL_CLASS)!!,
            keyFieldName,
            obj.getString(keyFieldName)!!
        )
    }

    override suspend fun relate(thisVertex: GraphEntity, with: String, thatVertex: GraphEntity): Result<Boolean> {
        var result: Result<Boolean> = Result(true)

        try {
            var fromVertex: Vertex? = null
            var toVertex: Vertex? = null

            /**
             * Find the vertices represented by the prototypes..
             * This is necessary because this class has a proxy for RPC and Vertex as a parameter is not serializable
             * so we have to pass in, basically, a string and find the actual object that it refers to.
             */
            val thisKey = thisVertex.getString(DBConstants.USE_KEY)!!
            val thatKey = thatVertex.getString(DBConstants.USE_KEY)!!

            /**
             * "id" is a reserved Vertex property, but it can be implemented however the Vertex implementation deems,
             * so the underlying type may be incompatible with JsonObject's properties. The safest type is String,
             * so we make a copy of the id as String (by calling toString() on the id object) and save it as INTERNAL_ID.
             * When we want to search by id, we tell Tinkerpop that the key field is "id", but the value is coming
             * from INTERNAL_ID. We will let the underlying implementation convert the String to whatever type they
             * need.
             */
            val keyValue1 = thisVertex.getString(thisKey)
            val keyValue2 = thatVertex.getString(thatKey)
            var vertices: Iterator<Vertex?>
            vertices = g!!.V().has(thisKey, keyValue1)
            if (vertices.hasNext()) {
                fromVertex = vertices.next()
            }
            vertices = g!!.V().has(thatKey, keyValue2)
            if (vertices.hasNext()) {
                toVertex = vertices.next()
            }
            if (fromVertex != null && toVertex != null) {
                fromVertex.addEdge(with, toVertex)
                graphDB!!.tx().commit()
            }
        } catch (e: Exception) {
            logger.error("An error occurred in relate:", e)
            graphDB!!.tx().rollback()
            result = Result(null, e)
        }

        return result
    }

    open protected fun delete(vertex: Vertex?, recursionLevel: Int = 0) {
        try {
            for (propertyKey in vertex!!.keys()) {
                if (vertex.value<Any>(propertyKey) is String) {
                    val keyValue = vertex.value<Any>(propertyKey) as String
                    if (keyValue.startsWith("array:") || keyValue.startsWith("object:")) {
                        val childVertices: Iterator<Vertex> = g!!.V(
                            vertex.id()
                        ).out(keyValue)
                        while (childVertices.hasNext()) {
                            delete(childVertices.next(), recursionLevel + 1)
                        }
                    }
                }
            }
            val currentClass = vertex.property<Any>(DBConstants.INTERNAL_CLASS).value() as String
            if (currentClass.contains(":") || recursionLevel == 0) {
                /**
                 * Delete all edges from this vertex...
                 */
                g!!.V(vertex.id()).bothE().drop().iterate()
                /**
                 * Delete vertex (it can also be expressed as g.V(vertex.id()).drop())
                 */
                vertex.remove()
            }
        } catch (e: Exception) {
            logger.error("An error occurred in delete:", e)
        }
    }

    override suspend fun delete(graphEntity: GraphEntity): Result<Boolean> {
        var result: Result<Boolean> = Result(true)
        val key = graphEntity.getString(DBConstants.USE_KEY)!!
        val entityId = graphEntity.getString(key)!!
        if (entityId == null) {
            logger.error("Invalid request to delete a vertex: entity has no ID!")
            result = Result(null, Exception("Invalid request to delete a vertex: entity has no ID!"))
        } else {
            var vertexToDelete: Vertex? = null
            try {
                vertexToDelete = g!!.V().has(key, entityId).next()
                delete(vertexToDelete)
                graphDB!!.tx().commit()
            } catch (e: NoSuchElementException) {
                logger.error("Vertex to be deleted was not found. _class=" + graphEntity.clazz + " _id=" + entityId)
                result = Result(null, e)
            }
        }

        return result
    }

    override suspend fun bulkRelate(
        thisVertex: GraphEntity,
        with: String,
        thoseVertices: List<GraphEntity>
    ): Result<Boolean> {
        val vertices: Iterator<Vertex>
        var result: Result<Boolean> = Result(true)

        try {
            var fromVertex: Vertex? = null
            val thisKey = thisVertex.getString(DBConstants.USE_KEY)!!
            val keyValue1 = thisVertex.getString(thisKey)
            vertices = g!!.V().has(thisKey, keyValue1)
            if (vertices.hasNext()) {
                fromVertex = vertices.next()
            }
            val it = thoseVertices.iterator()
            var toVertex: Vertex? = null
            var thatKey: String
            var vertices2: Iterator<Vertex?>
            var vertex: GraphEntity
            var keyValue2: String?
            while (it.hasNext()) {
                vertex = it.next()
                thatKey = vertex.getString(DBConstants.USE_KEY)!!
                keyValue2 = vertex.getString(thatKey)
                vertices2 = g!!.V().has(thatKey, keyValue2)
                if (vertices2.hasNext()) {
                    toVertex = vertices2.next()
                }
                if (fromVertex != null && toVertex != null) {
                    fromVertex.addEdge(with, toVertex)
                }
            }
            graphDB!!.tx().commit()
        } catch (e: Exception) {
            logger.error("An error occurred in relate:", e)
            graphDB!!.tx().rollback()
            result = Result(null, e)
        }

        return result
    }

    open fun getGraphDB(): Graph? {
        return graphDB
    }

    open protected fun getGraphEntityFrom(vertex: Vertex?): GraphEntity? {
        var result: GraphEntity? = null

        vertex?.let {
            result = GraphEntity(vertex)
        }

        return result
    }
}
