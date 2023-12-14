import com.asanasoft.graphdb.GraphEntity

class TestGraphDb {
    fun runTest() {
        val json = "{" +
                "  \"name\": \"Janus\"," +
                "  \"description\": \"JanusGraphDBService\"," +
                "  \"version\": \"0.0.1\"," +
                "  \"type\": \"GraphDBServiceProvider\"" +
                "}"

        val graphEntity  = GraphEntity(json)
        val graphEntity2 = GraphEntity(className = "GraphEntity", keyFieldToUse = "name", id = "Janus2")
        val graphEntity3 = GraphEntity()

        graphEntity3.add("name", "Janus2")
        graphEntity3.add("description", "JanusGraphDBService2")
        graphEntity3.add("version", "0.0.2")
        graphEntity3.add("type", "GraphDBServiceProvider2")
        graphEntity3.add("someNumber", 123)
        graphEntity3.add("someBoolean", true)
        graphEntity3.add("someNull", null)
        graphEntity3.addChild(graphEntity)
        graphEntity3.addChild(graphEntity2)

        println(graphEntity)
        println(graphEntity.toString())
        println("=====================================")

        val jsonString = graphEntity3.toString()

        println(jsonString)

        val graphEntity4 = GraphEntity(jsonString)

        println("=====================================")

        println(graphEntity4)
    }
}

fun main() {
    TestGraphDb().runTest()
}
