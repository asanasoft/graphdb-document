package com.asanasoft.graphdb.init

import mu.KotlinLogging
import java.util.*

private val logger = KotlinLogging.logger {}

object Environment : PropertiesLoader() {
    private var initialized : Boolean = false
    val SYSTEM_FILENAME = "system.properties"

    init {
        logger.info("Initializing Environment...")

        //See if the "system" properties has been set...
        if (multiMap.containsKey("system")) {
            systemProperties = multiMap.get("system")!!
        }

        // First, load all the environment properties...
        for (key in System.getenv().keys) {
            systemProperties.setProperty(key, System.getenv(key))
        }

        // Second, load all the system properties...
        for (key in System.getProperties().stringPropertyNames()) {
            systemProperties.setProperty(key, System.getProperty(key))
        }

        //load the main properties file...
        load(SYSTEM_FILENAME, systemProperties)
        multiMap.putIfAbsent("system", systemProperties)

        /**
         * Now Load all properties files as indicated in the main properties files.
         * This will find all keys with a ".properties" suffix and load the properties files
         * as indicated by the value.
         *
         * e.g., the entry <code>myProsFile.properties=system.properties</code> will load all properties from
         * "system.properties" into an entry with key "myPropsFile.properties"
         */
        var properties : Properties
        for (propsFilename in systemProperties.stringPropertyNames()) {
            if (propsFilename.endsWith(".properties")) {
                properties = Properties()
                load(systemProperties.getProperty(propsFilename), properties)
                multiMap.putIfAbsent(propsFilename, properties)
            }
        }

        initialized = true
    }
}