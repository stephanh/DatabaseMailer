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
  "com.twitter" % "querulous" % "2.6.5",
  "org.scala-tools.testing" %% "specs" % "1.6.9" % "test->default",
  "mysql" % "mysql-connector-java" % "5.1.15" % "compile->default",
  "ch.qos.logback" % "logback-classic" % "0.9.27" % "compile->default"
)
