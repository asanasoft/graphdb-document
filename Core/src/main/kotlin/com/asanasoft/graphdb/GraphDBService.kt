package com.asanasoft.graphdb

import com.asanasoft.util.Result

interface GraphDBService {
    suspend fun gremlinScript(params: Map<String, String>, script: String): Result<List<GraphEntity>>
    suspend fun gremlinScriptShallow(params: Map<String, String>, script: String): Result<List<GraphEntity>>
    suspend fun insertVertex(clazz: String, vertex: GraphEntity): Result<Boolean>
    suspend fun updateVertex(clazz: String, vertex: GraphEntity): Result<Boolean>
    suspend fun insertOrUpdateVertex(clazz: String, vertex: GraphEntity): Result<Boolean>
    suspend fun retrieveVertex(id: String): Result<GraphEntity>
    suspend fun retrieveVertex(id: String, getChildren: Boolean): Result<GraphEntity>
    suspend fun retrieveVertex(visitor: GraphEntity): Result<GraphEntity>
    suspend fun retrieveVertexByKey(key: String, value: String): Result<List<GraphEntity>>
    suspend fun retrieveVerticesByIds(ids: List<String>): Result<List<GraphEntity>>
    suspend fun retrieveVerticesByIds(ids: List<String>, getChildren: Boolean): Result<List<GraphEntity>>
    suspend fun relate(
        thisVertex: GraphEntity,
        with: String,
        thatVertex: GraphEntity
    ): Result<Boolean>

    suspend fun bulkRelate(thisVertex: GraphEntity, with: String, thoseVertices: List<GraphEntity>): Result<Boolean>

    suspend fun delete(jsonVertex: GraphEntity): Result<Boolean>
}
