package com.asanasoft.graphdb.init

import com.asanasoft.util.DBConstants.DEFAULT_PROPERTIES
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.Reader
import java.util.*
import kotlin.collections.HashMap

/**
 * Properties loader
 * This class loads and manages properties in a multi map
 * <code>Map<String, Properties></code>
 * Where the key is, usually, the properties filename, or
 * some arbitrary string.
 *
 * @constructor Create empty Properties loader
 */
open abstract class PropertiesLoader {
    protected var systemProperties : Properties = Properties()
    var multiMap : MutableMap<String, Properties> = HashMap()

    var multiValues : Boolean = false
        get() {
            return field
        }
        set(value) {
            field = value
        }

    open fun containsKey(key : String) : Boolean {
        return containsKey(DEFAULT_PROPERTIES, key)
    }

    open fun containsKey(inProperties : String, key : String) : Boolean {
        var result = false

        if (getProperties(inProperties) != null) {
            result = getProperties(inProperties)!!.containsKey(key)
        }

        return result
    }

    /**
     * Get property
     * Get a value from the system properties. This is a helper function.
     *
     * @param usingKey
     * @return
     */
    open fun getProperty(usingKey: String) : String? {
        return getProperty(fromProperties = DEFAULT_PROPERTIES, usingKey)
    }

    /**
     * Get property
     * Get a value using a key from propertiesKey
     * @param fromProperties
     * @param usingKey
     * @return String?
     */
    open fun getProperty(fromProperties: String, usingKey: String) : String? {
        var result : String? = null
        if (multiMap.containsKey(fromProperties)) {
            var map = multiMap.get(fromProperties)
            result = map?.getProperty(usingKey)
        }
        return result
    }

    /**
     * Get properties
     * Get a properties object assigned to <code>propertiesName</code>
     *
     * @param propertiesName
     * @return Properties?
     */
    open fun getProperties(propertiesName : String) : Properties? {
        return getProperties(propertiesName, false)
    }

    open fun getProperties(propertiesName : String, create : Boolean) : Properties? {
        var result : Properties?
        result = multiMap.get(propertiesName)

        if (result == null && create) {
            result = createProperties(propertiesName)
            load(propertiesName, result)
        }

        return result
    }

    /**
     * Create properties
     * Create a properties entry using <code>propertiesName</code>
     *
     * @param propertiesName
     * @return Properties
     */
    open fun createProperties(propertiesName : String) : Properties {
        var properties = MultiValueProperties()
        multiMap.put(propertiesName, properties)
        return properties
    }

    /**
     * Load
     *
     * @param propertiesFileName
     * @param intoProperties
     */
    open fun load(propertiesFileName: String, intoProperties: Properties) {

        //First, see if a resource directory has been defined...
        var baseDir = getProperty("base_dir") ?: "/" //if no base.dir, then prepend a slash to search the classpath...
        var fileName : String = baseDir + propertiesFileName

        readProperties(fileName, intoProperties)

        /**
         * ...now load environment-specific properties.
         * Again, these can override the system.properties...
         */
        if (getProperties(DEFAULT_PROPERTIES)!!.containsKey("ENV")) {
            fileName = baseDir + getProperties(DEFAULT_PROPERTIES)!!.getProperty("ENV") + "_" + propertiesFileName

            /**
             * Because properties support multi-values, we'll load the "overriding values"
             * into a new properties file, then we'll replace the same values in the original
             * properties with these...
             */
            val overrideProps = MultiValueProperties()
            readProperties(fileName, overrideProps)

            /**
             * Now override all the values, including ArrayLists...
             */
            for (key in overrideProps.keys) {
                intoProperties.replace(key, overrideProps.get(key))
            }
        }
    }

    /**
     * Read properties
     *
     * @param fromfileName
     * @param intoProperties
     * @return <code>true</code> if successful
     */
    protected fun readProperties(fromfileName: String, intoProperties: Properties) : Boolean {
        var result = true //no need for exceptions...

        var reader : Reader? = null

        try {
            var file = File(fromfileName)
            if (file.exists()) {
                reader = FileReader(file)
            }
            else {
                reader = getResourceAsReader(fromfileName)
            }

            if (reader != null) {
                intoProperties?.load(reader)
            }
        } catch (e: Exception) {
            result = false
        }

        return result
    }

    /**
     * Get resource as reader
     *
     * @param fileName
     * @return a <code>BufferedReader</code> representing <code>filenName</code>
     */
    protected fun getResourceAsReader(fileName: String) : BufferedReader {
        var result : Reader? = null
        result = this::class.java.getResourceAsStream(fileName).bufferedReader()
        return result
    }

    /**
     * This class will detect multi-valued properties and place them in an ArrayList...
     */
    inner class MultiValueProperties : Properties() {
        override fun put(key: Any?, value: Any?): Any? {

            if (containsKey(key) && multiValues) {
                val multiValArray : ArrayList<Any?>
                var multiVal = get(key)

                if (multiVal !is ArrayList<*>) {
                    multiValArray = ArrayList<Any?>()
                    multiValArray.add(multiVal)
                    super.put(key, multiValArray)
                }
                else {
                    multiValArray = multiVal as ArrayList<Any?>
                }

                multiValArray.add(value)
            }
            else {
                super.put(key, value)
            }

            return value
        }
    }
}