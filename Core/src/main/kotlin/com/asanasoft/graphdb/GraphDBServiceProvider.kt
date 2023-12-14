package com.asanasoft.graphdb

interface GraphDBServiceProvider {
    fun create(): GraphDBService
}