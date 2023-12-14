package com.asanasoft.graphdb.provider

import com.asanasoft.graphdb.GraphDBService
import com.asanasoft.graphdb.GraphDBServiceProvider

class Bitsy : GraphDBServiceProvider {
    override fun create(): GraphDBService {
        return BitsyGraphDBService()
    }
}