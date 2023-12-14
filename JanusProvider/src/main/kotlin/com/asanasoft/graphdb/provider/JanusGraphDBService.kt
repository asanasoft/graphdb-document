package com.asanasoft.graphdb.provider

import com.asanasoft.graphdb.impl.AbstractGraphDBService
import org.apache.tinkerpop.gremlin.structure.Graph
import org.janusgraph.core.JanusGraphFactory

class JanusGraphDBService : AbstractGraphDBService() {
    private val DEFAULT_DBNAME = "janus-graph.db"
    private var dbName : String = DEFAULT_DBNAME

    fun JanusGraphDBService(dbName: String) {
        this.dbName = dbName
        val janusGraph: Graph = JanusGraphFactory.open(dbName)
        this.setGraphDB(janusGraph)
    }
}