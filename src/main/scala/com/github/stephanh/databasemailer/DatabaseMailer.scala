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
              

  /**
   * The main method. Here we parse the config file and call the methods to run the queries and send the emails.
   *  @param args command line arguments
   */
  def main(args: Array[String]) {
    
    if(args.length != 1) {
      println("Need to provide path to configuration file")
      error("Need to provide path to configuration file")
      exit(1)
    }

    // Parse config file
    val configFile = args(0)
    val config = Configuration(Source.fromFile(configFile))
    val generalTemplate = XML.loadFile(config.get("general", "template").getOrElse(throw new ConfigException("Need template attribute in general section")))
    // Get database settings and set up async connection pool.
    val dbSettings = DBConnectionSettings(config.getSection("general").getOrElse(throw new ConfigException("Need general selection in config file")))
    val queryEvaluator = AsyncQueryEvaluator(dbSettings.host, dbSettings.user, dbSettings.password)

    // Get email server settings and connec to email server.
    val emailServer = connectToEmailServer(config.getSection("general").getOrElse(throw new ConfigException("Need general selection in config file")))

    // Parse queries to be run and run queries async, storing futures
    // for the query results in a map
    val querySectionRegex = ("^" + querySectionPrefix + ".*").r
    val queryResults: Map[String, Future[NodeSeq]] = config.matchingSections(querySectionRegex) map processQueries(queryEvaluator) toMap

    // Parse emails to be sent and send the emails in parallel
    val emailSectionRegex = ("^" + emailSectionPrefix + ".*").r
    config.matchingSections(emailSectionRegex).par foreach sendEmail(emailServer, queryResults, generalTemplate)

    queryEvaluator.shutdown()
    info("Finished.")
    exit(0)
  }

  /**
   * Parse the config for the query information and run the required queries asynchronously returning the query name and a future with the query result
   * rendered as XML using the specified template for the query.
   * @param queryEvaluator asynchronous pool of database connections
   * @param section the config section for this query
   * @return Tuple with name of query as specified in the config file and a Future holding the query result rendered as XML using the template specified in the config.
   */
  def processQueries(queryEvaluator: AsyncQueryEvaluator)(section: Section): (String, Future[NodeSeq]) = {
    info("Processing query " + section.name)

    // Get the query to run
    val sql = section.options.get("sql").getOrElse(throw new ConfigException("%s needs to include \"sql\" attribute" format (section.name)))
    val templateFilename = section.options.get("template").getOrElse(throw new ConfigException("%s needs to include \"template\" attribute" format (section.name)))

    // Run and render the query in the template
    val result = runQuery(sql, queryEvaluator).map(renderQuery(templateFilename))

    // Return the query along with the query name in the config file
    (section.name -> result)
  }


  /**
   * Runs the actual query
   * @param query
   * @param queryEvaluator
   * @return a sequence of Maps mapping the column name or column number (i) as in "coli" to the corresponding value for each row returned by the query.
   */
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


  /**
   * Transforms the query result into XML
   * @param templateFilename the template to use for the query result
   * @param queryOutput the query result
   * @return the query result in XML using the template
   */
  def renderQuery(templateFilename: String)(queryOutput: Seq[Map[String, String]]): NodeSeq = {
	  val transformer = new QueryTransformer(queryOutput)
	  transformer(XML.loadFile(templateFilename))
  }


  /**
   * Send the email for the supplied config section. It takes all the query results, waits for the ones that need to be sent in this email, renders the email
   * using the template specified in the config and the general template and sends the email.
   * @param emailServer
   * @param queryResults A map of all the future query results
   * @param generalTemplateFile general template file for all emails
   * @param section config section for this particular email
   */
  def sendEmail(emailServer: Session, queryResults: Map[String, Future[NodeSeq]], generalTemplateFile: Elem)(section: Section) {
	  info("Processing email " + section.name)

	  // Get all the config options
	  val addresses: Array[String] = section.options.get("addresses").getOrElse(throw new ConfigException("%s needs to include \"addresses\" attribute" format (section.name))).split(",").map(_.trim)
	  val subject = section.options.get("subject").getOrElse(throw new ConfigException("%s needs to include \"subject\" attribute" format (section.name)))
	  val from = section.options.get("from").getOrElse(throw new ConfigException("%s needs to include \"from\" attribute" format (section.name)))
	  val queryNames: Array[String]  = section.options.get("query_names").getOrElse(throw new ConfigException("%s needs to include \"query_names\" attribute" format (section.name))).split(",").map(_.trim)
	  val templateFilename = section.options.get("template").getOrElse(throw new ConfigException("%s needs to include \"template\" attribute" format (section.name)))

	  // Get the query results required for this email
	  val queryOutputs: List[NodeSeq] = queryNames.toList map (queryResults(_).get())

	  // Generate the emails inserting the results into the email specific template and then the general template
	  debug("Putting results into template for " + section.name)
	  val transformer = new EmailTransformer(queryOutputs)
	  val generalTransformer = new GeneralTransformer(transformer(XML.loadFile(templateFilename)))
	  val emailContent =  generalTransformer(generalTemplateFile)

	  // Send the email
	  debug("Sending email for " + section.name)
	  postMail(emailServer, addresses, subject, emailContent.toString, from)
	  debug(section.name + " has sent email")
  }

  /**
   * Sends the email.
   * @param emailServer
   * @param recipients
   * @param subject
   * @param content
   * @param from
   */
  def postMail(emailServer: Session, recipients: Array[String], subject: String, content: String , from: String) {
	  // create a message
	  val message = new MimeMessage(emailServer)
	  message.setSubject(subject)
	  message.setFrom(new InternetAddress(from))

	  // Set recipients
	  for(recip <- recipients) {
		  message.addRecipient(Message.RecipientType.TO, new InternetAddress(recip))
	  }

	  message.setContent(content, "text/html")

	  debug("Sending message")
	  Transport.send(message)
  }


  /**
   * Create a session to the email server.
   * @param section General confi section with the email server information.
   * @return connection to the email server
   */
  def connectToEmailServer(section: Section): Session = {
		  val prop = new Properties()
		  prop.setProperty("mail.smtp.host", section.options.get("smtp.host").getOrElse(throw new ConfigException("%s needs to include \"smtp.host\" attribute" format (section.name))))
		  prop.setProperty("mail.smtp.port", section.options.getOrElse("smtp.port", "25"))

		  val username = section.options.getOrElse("smpt.username", "")

		  if(username != "") {
			  val password = section.options.getOrElse("smpt.password", "")
					  val authenticator = new Authenticator(username, password)

			  prop.setProperty("mail.smtp.auth", "true")

			  return Session.getInstance(prop, authenticator)

		  } else {
			  prop.setProperty("mail.smtp.auth", "false")

			  return Session.getInstance(prop)
		  }
  }


  private class Authenticator(val username: String, val password: String) extends javax.mail.Authenticator {
    private val authentication = new PasswordAuthentication(username, password)
    
    override protected def getPasswordAuthentication(): PasswordAuthentication = return authentication
  }

  
  private class QueryTransformer(queryResult: Seq[Map[String, String]]) extends Transformer {
    $(".result-row") { node =>
	  queryResult.flatMap { r =>
	    new Transform(node) {
	  	  for((key, value) <- r) $("." + key).contents = value
	    }
	  }
    }
  }


  private class EmailTransformer(queryResults: List[NodeSeq]) extends Transformer {
    $(".query-result") { node =>
	  queryResults.flatMap { r =>
	    new Transform(node) {
		  $(".query").contents = r
	    }
	  }
	}
  }


  private class GeneralTransformer(email: NodeSeq) extends Transformer {
    $(".content").contents = email
  }


  private case class DBConnectionSettings(val host: String, val user: String, val password: String = "", val port: Int = 3306)

  private object DBConnectionSettings {
    def apply(section: Section): DBConnectionSettings =
      new DBConnectionSettings(section.options.get("db.host").getOrElse(throw new ConfigException("%s needs to include \"db.host\" attribute" format (section.name))),
        section.options.get("db.username").getOrElse(throw new ConfigException("%s needs to include \"db.username\" attribute" format (section.name))),
        section.options.getOrElse("db.password", ""),
        section.options.getOrElse("db.port", "3306").toInt)
  }
}



