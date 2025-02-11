package org.squerall

import com.google.common.collect.ArrayListMultimap
import com.typesafe.scalalogging.Logger
import org.squerall.Helpers._
import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, Reads, __}

import java.util
import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * Created by mmami on 06.07.17.
 */
class Planner(stars: mutable.HashMap[String, mutable.Set[(String, String)]] with mutable.MultiMap[String, (String, String)]) {

  val logger: Logger = Logger("Squerall")

  def getNeededPredicates(star_predicate_var: mutable.HashMap[(String, String), String], joins: ArrayListMultimap[String, (String, String)], select_vars: util.List[String], groupBys: (ListBuffer[String], mutable.Set[(String, String)]), prefixes: Map[String, String]): (mutable.Set[String], mutable.Set[(String, String)]) = {

    logger.info("star_predicate_var: " + star_predicate_var)
    val predicates: mutable.Set[String] = mutable.Set.empty
    val predicatesForSelect: mutable.Set[(String, String)] = mutable.Set.empty

    val join_left_vars = joins.keySet()
    val join_right_vars = joins.values().asScala.map(x => x._1).toSet // asScala, converts Java Collection to Scala Collection

    val join_left_right_vars = join_right_vars.union(join_left_vars.asScala)

    logger.info("--> All (left & right) join operands: " + join_left_right_vars)

    star_predicate_var.foreach(t => {
      val s_p = t._1
      val o = t._2

      val occurrences = star_predicate_var groupBy (_._2) mapValues (_.size) // To capture variables (objects) used in more than one predicate

      if (select_vars.contains(o.replace("?", "")) || join_left_vars.contains(o) || join_right_vars.contains(o) || occurrences(o) > 1)
        predicates.add(s_p._2)

      if (select_vars.contains(o.replace("?", "")))
        predicatesForSelect.add(s_p)

      if (groupBys != null) {
        // Forming e.g. "failure_isFailureOf_fsmt"
        val groupByPredicate = s_p._1.replace("?", "") + "_" + omitNamespace(s_p._2) + "_" + prefixes(get_NS_predicate(s_p._2)._1)

        if (groupBys._2.map(_._1).contains(groupByPredicate)) { // map to get only cols eg failure_isFailureOf from Set((failure_isFailureOf_fsmt,count))
          predicates.add(s_p._2)
        }
      }
    })
    (predicates, predicatesForSelect)
  }

  def generateJoinPlan: (ArrayListMultimap[String, (String, String)], mutable.Set[String], mutable.Set[String], Map[(String, String), String]) = {

    val keys = stars.keySet.toSeq
    logger.info("Stars: " + keys.toString())
    val joins: ArrayListMultimap[String, (String, String)] = ArrayListMultimap.create[String, (String, String)]()
    var joinPairs: Map[(String, String), String] = Map()

    val joinedToFlag: mutable.Set[String] = mutable.Set()
    val joinedFromFlag: mutable.Set[String] = mutable.Set()

    keys.indices.foreach(i => {
      val currentSubject = keys(i)
      val valueSet = stars(currentSubject)
      valueSet.foreach(p_o => {
        val o = p_o._2
        if (keys.contains(o)) { // A previous star of o
          val p = p_o._1
          joins.put(currentSubject, (o, p))
          joinPairs += (omitQuestionMark(currentSubject), omitQuestionMark(o)) -> p
          joinedToFlag.add(o)
          joinedFromFlag.add(currentSubject)
        }
      })
    })
    (joins, joinedToFlag, joinedFromFlag, joinPairs)
  }

  def reorder(joins: ArrayListMultimap[String, (String, String)], starWeights: Map[String, Double]): ListMap[(String, String), Double] = {
    logger.info("...REORDERING JOINS, if needed...")
    val joinsToReorder: ListBuffer[(String, String)] = ListBuffer()
    joins.entries().forEach(j => {
      joinsToReorder += ((j.getKey, j.getValue._1))
    })
    val scoredJoins = getScoredJoins(joins, starWeights)
    val sortedScoredJoins = ListMap(scoredJoins.toSeq.sortWith(_._2 > _._2): _*)
    sortedScoredJoins
  }

  def getScoredJoins(joins: ArrayListMultimap[String, (String, String)], scores: Map[String, Double]): Map[(String, String), Double] = {
    var scoredJoins: Map[(String, String), Double] = Map()
    joins.entries().forEach(j => {
      scoredJoins += (j.getKey, j.getValue._1) -> (scores(j.getKey) + scores(j.getValue._1))
    })
    scoredJoins
  }

  def sortStarsByWeight(starDataTypesMap: Map[String, mutable.Set[String]], filters: Map[String, Integer], configFile: String): Map[String, Double] = {

    val queryString = scala.io.Source.fromFile(configFile)
    val configJSON = try queryString.mkString finally queryString.close()

    case class ConfigObject(datasource: String, weight: Double)

    implicit val userReads: Reads[ConfigObject] = (
      (__ \ 'datasource).read[String] and
        (__ \ 'weight).read[Double]
      ) (ConfigObject)

    val weights = (Json.parse(configJSON) \ "weights").as[Seq[ConfigObject]]

    var scoresByDatasource: Map[String, Double] = Map()

    weights.foreach(w => {
      scoresByDatasource += w.datasource -> w.weight
    })
    logger.info(s"- We use the following scores of the data source types: $scoresByDatasource")

    val scores = starScores(starDataTypesMap, scoresByDatasource, filters)

    scores
  }

  def starScores(starDataTypesMap: Map[String, mutable.Set[String]], weightsByDatasource: Map[String, Double], filters: Map[String, Integer]): Map[String, Double] = {
    var scores: Map[String, Double] = Map()

    var datasourceTypeWeight = 0.0 // Coucou!

    starDataTypesMap.foreach(s => {
      val star = s._1 // eg. ?r
      val datasourceTypeURI_s = s._2 // eg. http://purl.org/db/nosql#cassandra

      val nbrFilters = filters(star).toInt

      if (datasourceTypeURI_s.size == 1) { // only one relevant datasource
        val datasourceType = datasourceTypeURI_s.head.split("#")(1) // eg. cassandra

        // datasourceTypeWeight = weightsByDatasource(datasourceType) + nbrFilters
        if (nbrFilters > 0)
          datasourceTypeWeight = weightsByDatasource(datasourceType) + 1
        else
          datasourceTypeWeight = weightsByDatasource(datasourceType)
        // Add up the number of filters to the score of the star
      }
      // else, we keep 0, as we are assuming if there are more than 1 data sources, queryig & union-ing them would be expensive
      scores += (star -> datasourceTypeWeight)
    })
    scores
  }
}
