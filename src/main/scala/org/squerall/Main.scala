package org.squerall

import org.apache.spark.sql.DataFrame

/**
 * Created by mmami on 26.01.17.
 */
object Main extends App {

  val mappingsFile = "/home/sali/root/input/mappings.ttl"
  val configFile = "/home/sali/root/input/config"
  val executorID = "local[*]"
  val reorderJoin = "n"
  val queryEngine = "s"
  val outputPath = "/home/sali/result"
  var queryFile = "/home/sali/root/input/queries/Q8.sparql"

  if (queryEngine == "s") { // Spark as query engine
    val executor: SparkExecutor = new SparkExecutor(executorID, mappingsFile, outputPath)

    val run = new Run[DataFrame](executor)
    run.application(queryFile, mappingsFile, configFile)

  }
}
