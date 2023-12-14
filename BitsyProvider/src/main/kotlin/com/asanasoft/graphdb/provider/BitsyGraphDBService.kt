package com.asanasoft.graphdb.provider

import com.asanasoft.graphdb.impl.AbstractGraphDBService
import com.lambdazen.bitsy.BitsyGraph
import com.lambdazen.bitsy.BitsyIsolationLevel
import com.lambdazen.bitsy.wrapper.BitsyAutoReloadingGraph
import org.apache.tinkerpop.gremlin.structure.Graph
import java.nio.file.Path
import java.nio.file.Paths

class BitsyGraphDBService : AbstractGraphDBService() {
    private val DEFAULT_DBNAME = "bitsy-graph.db"
    private var dbName : String = DEFAULT_DBNAME

    fun BitsyGraphDBService(dbName: String) {
        this.dbName = dbName

        val dbPath : Path = Paths.get(dbName)

        if (!dbPath.toFile().exists()) {
            dbPath.toFile().mkdirs()
        }

        val bitsyGraphOrig: BitsyGraph = BitsyGraph(dbPath)
        bitsyGraphOrig.setDefaultIsolationLevel(BitsyIsolationLevel.READ_COMMITTED)
        bitsyGraphOrig.setReorgFactor(3.0)
        val bitsyGraph: Graph = BitsyAutoReloadingGraph(bitsyGraphOrig)

        this.setGraphDB(bitsyGraph)
    }
}