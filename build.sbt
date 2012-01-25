organization := "com.github.stephanh"

name := "DatabaseMailer"

version := "0.1"

scalaVersion := "2.9.1"

// scalacOptions += "-deprecation"

resolvers += "Java.net Maven2 Repository" at "http://download.java.net/maven/2/"

resolvers += "Scala Tools Releases" at "http://scala-tools.org/repo-releases/"

resolvers += "Scala Tools Snapshots" at "http://scala-tools.org/repo-snapshots/"

resolvers += "Querulous" at "http://maven.twttr.com/"

libraryDependencies ++= Seq(
  "javax.mail" % "mail" % "1.4.4",
  "com.twitter" % "querulous" % "2.6.5",
  "com.twitter" %% "util-core" % "1.12.12",
  "org.clapper" %% "grizzled-scala" % "1.0.9",
  "org.clapper" %% "grizzled-slf4j" % "0.6.6",
  "org.fusesource.scalate" % "scalate-wikitext" % "1.5.3",
  "org.fusesource.scalate" % "scalate-page" % "1.5.3",
  "org.scala-tools.testing" %% "specs" % "1.6.9" % "test->default",
  "ch.qos.logback" % "logback-classic" % "0.9.27" % "compile->default"
)
