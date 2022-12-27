package org.sqltonosql

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import io.gatling.jsonpath._
import org.sqltonosql.loaders.Loader

object Main extends App {
  val inputSQLDump = "/home/sali/data/input/04Product.sql"// /path/to/08Offer.sql
  val entityName = "Product" // e.g. "Offer"
  val configFile = "/home/sali/root/input/config" // "/path to config file containing how to access database to store Offer, see evaluation/ folder for example "

  val fileSource = scala.io.Source.fromFile(configFile)
  val fileContent = fileSource.getLines().mkString
  fileSource.close()

  val json = new ObjectMapper().readValue(fileContent, classOf[JsonNode])


  entityName match {
    case ("Offer") =>
      val resURl = JsonPath.query("$.sources[?(@.entity == 'Offer')].options.url", json).right.map(_.toVector)
      val url = resURl match {
        case Right(Vector(url)) => url
        case _ => /*default -> fail*/
      }

      val resDatabase = JsonPath.query("$.sources[?(@.entity == 'Offer')].options.database", json).right.map(_.toVector)
      val database = resDatabase match {
        case Right(Vector(database)) => database
        case _ => /*default -> fail*/
      }

      val resCollection = JsonPath.query("$.sources[?(@.entity == 'Offer')].options.collection", json).right.map(_.toVector)
      val collection = resCollection match {
        case Right(Vector(collection)) => collection
        case _ => /*default -> fail*/
      }

      println(s"MongoDB: $url + $database + $collection")

      val loader = Loader(configFile)

      loader.offer(inputSQLDump, url.toString, database.toString, collection.toString)

    case ("Product") =>
      val resKeyspacel = JsonPath.query("$.sources[?(@.entity == 'Product')].options.keyspace", json).right.map(_.toVector)
      val keyspace = resKeyspacel match {
        case Right(Vector(keyspace)) => keyspace
        case _ => /*default -> fail*/
      }

      val resTable = JsonPath.query("$.sources[?(@.entity == 'Product')].options.table", json).right.map(_.toVector)
      val table = resTable match {
        case Right(Vector(table)) => table
        case _ => /*default -> fail*/
      }

      println(s"Cassandra: $keyspace + $table")

      val loader = new Loader(configFile)

      loader.product(inputSQLDump, keyspace.toString, table.toString)

    case ("Review") =>
      val resOutputFile = JsonPath.query("$.sources[?(@.entity == 'Review')].source", json).right.map(_.toVector)
      val outputFile = resOutputFile match {
        case Right(Vector(outputFile)) => outputFile
        case _ => /*default -> fail*/
      }

      println(s"Parquet: $outputFile")

      val loader = Loader(configFile)

      loader.review(inputSQLDump, outputFile.toString)

    case ("Person") =>
      val resHeader = JsonPath.query("$.sources[?(@.entity == 'Person')].options.header", json).right.map(_.toVector)
      val header = resHeader match {
        case Right(Vector(header)) => header
        case _ => /*default -> fail*/
      }

      val resDelimiter = JsonPath.query("$.sources[?(@.entity == 'Person')].options.delimiter", json).right.map(_.toVector)
      val delimiter = resDelimiter match {
        case Right(Vector(delimiter)) => delimiter
        case _ => /*default -> fail*/
      }

      val resMode = JsonPath.query("$.sources[?(@.entity == 'Person')].options.mode", json).right.map(_.toVector)
      val mode = resMode match {
        case Right(Vector(mode)) => mode
        case _ => /*default -> fail*/
      }

      val resOutputFile = JsonPath.query("$.sources[?(@.entity == 'Person')].source", json).right.map(_.toVector)
      val outputFile = resOutputFile match {
        case Right(Vector(outputFile)) => outputFile
        case _ => /*default -> fail*/
      }

      println(s"CSV: $header + $delimiter + $mode + $outputFile")

      val loader = Loader(configFile)

      loader.person(inputSQLDump, header.toString, delimiter.toString, mode.toString, outputFile.toString)
  }
}
