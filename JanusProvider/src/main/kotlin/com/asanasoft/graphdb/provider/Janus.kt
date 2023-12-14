package com.asanasoft.graphdb.provider

import com.asanasoft.graphdb.GraphDBService
import com.asanasoft.graphdb.GraphDBServiceProvider

class Janus : GraphDBServiceProvider {
    override fun create(): GraphDBService {
        return JanusGraphDBService()
    }
}