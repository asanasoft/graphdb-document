package com.asanasoft.graphdb

import java.nio.file.ProviderNotFoundException
import java.util.ArrayList
import java.util.ServiceLoader
import com.asanasoft.graphdb.init.Environment
import java.util.StringTokenizer

object GraphDBServiceLoader {
    //All providers
    fun providers(): List<GraphDBServiceProvider> {
        val services: MutableList<GraphDBServiceProvider> = ArrayList<GraphDBServiceProvider>()
        val loader: ServiceLoader<GraphDBServiceProvider> = ServiceLoader.load(GraphDBServiceProvider::class.java)
        loader.forEach {
            services.add(it)
        }

        return services
    }

    fun provider(name: String): GraphDBServiceProvider {
        var result: GraphDBServiceProvider? = null

        val services: List<GraphDBServiceProvider> = providers()
        for (service in services) {
            var tokenizer : StringTokenizer = StringTokenizer(service.javaClass.name, ".")
            var serviceName : String = tokenizer.toList().last() as String

            if (service.javaClass.name.equals(serviceName, ignoreCase = true)) {
                result = service
            }
        }

        if (result == null) {
            throw ProviderNotFoundException("No GraphDBServiceProvider found for name: $name")
        }

        return result
    }

    fun provider(): GraphDBServiceProvider {
        val defaultProvider = Environment.getProperty("defaultGraphDBProvider")

        return provider(defaultProvider!!)
    }
}
