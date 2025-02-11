package org.squerall

import org.apache.spark.sql.DataFrame

object Main extends App {

  var queryFile = args(0)
  val mappingsFile = args(1)
  val configFile = args(2)
  val executorID = args(3)
  val reorderJoin = args(4)
  val queryEngine = args(5)
  val outputPath = args(6)

  if (queryEngine == "s") { // Spark as query engine
    val executor: SparkExecutor = new SparkExecutor(executorID, mappingsFile, outputPath)
    val run = new Run[DataFrame](executor)
    run.application(queryFile, mappingsFile, configFile)
  } else if (queryEngine == "p") { // Presto as query engine
    val executor: PrestoExecutor = new PrestoExecutor(executorID, mappingsFile)
    val run = new Run[DataQueryFrame](executor)
    run.application(queryFile, mappingsFile, configFile)
  }
}
