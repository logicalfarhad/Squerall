package org.squerall

import org.apache.spark.sql.DataFrame

/**
  * Created by mmami on 26.01.17.
  */
object Main extends App {

    var queryFile = "/home/sali/root/input/queries/Q2.sparql"
    val mappingsFile = "/home/sali/root/input/mappings.ttl"
    val configFile = "/home/sali/root/input/config"
    val executorID = "local[*]"
    val reorderJoin = "n"
    val queryEngine = "s"

    if (queryEngine == "s") { // Spark as query engine
        val executor : SparkExecutor = new SparkExecutor(executorID, mappingsFile)

        val run = new Run[DataFrame](executor)
        run.application(queryFile,mappingsFile,configFile,executorID)

    } else if(queryEngine == "p") { // Presto as query engine
        val executor : PrestoExecutor = new PrestoExecutor(executorID, mappingsFile)
        val run = new Run[DataQueryFrame](executor)
        run.application(queryFile,mappingsFile,configFile,executorID)
    }

}
