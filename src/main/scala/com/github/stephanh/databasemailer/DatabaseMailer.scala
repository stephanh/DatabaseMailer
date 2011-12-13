import scala.io.Source
import scala.collection.Map
import scala.xml.{XML, NodeSeq}

import grizzled.config.{Configuration, ConfigException, Section}

import com.twitter.querulous.evaluator.QueryEvaluator

import org.fusesource.scalate.scuery._

object DatabaseMailer {
  val querySectionPrefix = "query_"

  def main(args: Array[String]) {
    val configFile = args(0)

    val config = Configuration(Source.fromFile(configFile))

    val defaultDBSettings = DBConnectionSettings(config.getSection("general").getOrElse(throw new ConfigException("Need general selection in config file")))


    val querySectionRegex = ("^" + querySectionPrefix + ".*").r
    config.forMatchingSections(querySectionRegex)(processQueries(defaultDBSettings))

  }

  def processQueries(dbSettings: DBConnectionSettings)(section: Section) {
    

    new QueryRender(section.name.drop(querySectionPrefix length),
                    section.options.get("sql").getOrElse(throw new ConfigException("%s needs to include \"sql\" attribute" format (section.name))),
                    section.options.get("template").getOrElse(throw new ConfigException("%s needs to include \"template\" attribute" format (section.name))),
                    dbSettings)
  }
}

case class DBConnectionSettings(val host: String, val user: String, val password: String = "", val port: Int = 3306)

object DBConnectionSettings {
  def apply(section: Section): DBConnectionSettings =
    new DBConnectionSettings(section.options.get("db.host").getOrElse(throw new ConfigException("%s needs to include \"db.host\" attribute" format (section.name))),
                             section.options.get("db.username").getOrElse(throw new ConfigException("%s needs to include \"db.username\" attribute" format (section.name))),
                             section.options.getOrElse("db.password", ""),
                             section.options.getOrElse("db.port", "3306").toInt)
}
    

class QueryRender(name: String, query: String, templateFile: String, dbSettings: DBConnectionSettings) {
  val queryOutput = renderQuery(runQuery(query, dbSettings), templateFile)

  def runQuery(query: String, dbSettings: DBConnectionSettings): Seq[Map[String, String]] = { 
    val queryEvaluator = QueryEvaluator(dbSettings.host, dbSettings.user, dbSettings.password) 
    val result: Seq[Map[String, String]] = queryEvaluator.select(query) {
      row => {
        val metaData = row.getMetaData
        (for (i <- 1 to metaData.getColumnCount;
              label = metaData.getColumnLabel(i);
              value = row.getString(i);
              key <- List(label, "col" + i.toString))
         yield (key -> value)) toMap
      }
    }
    
    result
  }
 
  def renderQuery(queryOutput: Seq[Map[String, String]], templateFile: String): NodeSeq = {
    val transformer = new QueryTransformer(queryOutput)
    transformer(XML.loadFile(templateFile))
  }
}

class QueryTransformer(queryResult: Seq[Map[String, String]]) extends Transformer {
  $(".result-row") { node =>
    queryResult.flatMap { r =>
      new Transform(node) {
        for((key, value) <- r) $("." + key).contents = value
      }
    }
  }
}
    

