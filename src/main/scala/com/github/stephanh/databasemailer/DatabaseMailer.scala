import scala.io.Source
import scala.collection.immutable.Map
import scala.xml.{XML, NodeSeq, Elem}

import javax.mail._
import javax.mail.internet._
import javax.mail.Authenticator
import javax.mail.PasswordAuthentication
import java.util.Properties

import grizzled.config.{Configuration, ConfigException, Section}
import grizzled.slf4j.Logging

import com.twitter.querulous.evaluator.QueryEvaluator
import com.twitter.querulous.async.AsyncQueryEvaluator
import com.twitter.util.Future
import com.twitter.conversions.time._

import org.fusesource.scalate.scuery._

object DatabaseMailer extends Logging {
  val querySectionPrefix = "query_"
  val emailSectionPrefix = "email_"


  def main(args: Array[String]) {
    val configFile = args(0)

    val config = Configuration(Source.fromFile(configFile))

    val dbSettings = DBConnectionSettings(config.getSection("general").getOrElse(throw new ConfigException("Need general selection in config file")))
    val queryEvaluator = AsyncQueryEvaluator(dbSettings.host, dbSettings.user, dbSettings.password)

    val generalTemplate = XML.loadFile(config.get("general", "template").getOrElse(throw new ConfigException("Need template attribute in general section")))

    val querySectionRegex = ("^" + querySectionPrefix + ".*").r
    val queryResults: Map[String, Future[NodeSeq]] = config.matchingSections(querySectionRegex) map processQueries(queryEvaluator) toMap

    val emailSectionRegex = ("^" + emailSectionPrefix + ".*").r
    config.matchingSections(emailSectionRegex).par foreach sendEmail(queryResults, generalTemplate)

  }


  def processQueries(queryEvaluator: AsyncQueryEvaluator)(section: Section): (String, Future[NodeSeq]) = {
    info("Processing query " + section.name)

    val sql = section.options.get("sql").getOrElse(throw new ConfigException("%s needs to include \"sql\" attribute" format (section.name)))
    val templateFilename = section.options.get("template").getOrElse(throw new ConfigException("%s needs to include \"template\" attribute" format (section.name)))



    val result = runQuery(sql, queryEvaluator).map(renderQuery(templateFilename))

    (section.name -> result)
  }


  def runQuery(query: String, queryEvaluator: AsyncQueryEvaluator): Future[Seq[Map[String, String]]] = queryEvaluator.select(query) {
      row => {
        val metaData = row.getMetaData
        (for (i <- 1 to metaData.getColumnCount;
              label = metaData.getColumnLabel(i);
              value = row.getString(i);
              key <- List(label, "col" + i.toString))
         yield (key -> value)) toMap
      }
  }


  def renderQuery(templateFilename: String)(queryOutput: Seq[Map[String, String]]): NodeSeq = {
    val transformer = new QueryTransformer(queryOutput)
    transformer(XML.loadFile(templateFilename))
  }


  def sendEmail(queryResults: Map[String, Future[NodeSeq]], generalTemplateFile: Elem)(section: Section) {
    info("Processing query " + section.name)

    val addresses: Array[String] = section.options.get("addresses").getOrElse(throw new ConfigException("%s needs to include \"addresses\" attribute" format (section.name))).split(",").map(_.trim)
    val queryNames: Array[String]  = section.options.get("query_names").getOrElse(throw new ConfigException("%s needs to include \"query_names\" attribute" format (section.name))).split(",").map(_.trim)
    val templateFilename = section.options.get("template").getOrElse(throw new ConfigException("%s needs to include \"template\" attribute" format (section.name)))
    
    val queryOutputs: List[NodeSeq] = queryNames.toList map (queryResults(_).get())

    val transformer = new EmailTransformer(queryOutputs)
    val generalTransformer = new GeneralTransformer(transformer(XML.loadFile(templateFilename)))
    val emailContent =  generalTransformer(generalTemplateFile)
   
    println(emailContent)
  }
}

class EmailTransformer(queryResults: List[NodeSeq]) extends Transformer {
  $(".query-result") { node =>
    queryResults.flatMap { r =>
      new Transform(node) {
        $(".query").contents = r
      }
    }
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


class GeneralTransformer(email: NodeSeq) extends Transformer {
  $(".content").contents = email
}


case class DBConnectionSettings(val host: String, val user: String, val password: String = "", val port: Int = 3306)

object DBConnectionSettings {
  def apply(section: Section): DBConnectionSettings =
    new DBConnectionSettings(section.options.get("db.host").getOrElse(throw new ConfigException("%s needs to include \"db.host\" attribute" format (section.name))),
                             section.options.get("db.username").getOrElse(throw new ConfigException("%s needs to include \"db.username\" attribute" format (section.name))),
                             section.options.getOrElse("db.password", ""),
                             section.options.getOrElse("db.port", "3306").toInt)
}
