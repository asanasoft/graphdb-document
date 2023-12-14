package com.asanasoft.graphdb.provider

import com.asanasoft.graphdb.impl.AbstractGraphDBService
import org.apache.tinkerpop.gremlin.structure.Graph
import org.janusgraph.core.JanusGraphFactory
import java.nio.file.Path
import java.nio.file.Paths

class JanusGraphDBService : AbstractGraphDBService() {
    private val DEFAULT_DBNAME = "bitsy-graph.db"
    private var dbName : String = DEFAULT_DBNAME

    fun JanusGraphDBService(dbName: String) {
        this.dbName = dbName
        val janusGraph: Graph = JanusGraphFactory.open(dbName)
        this.setGraphDB(janusGraph)
    }
}