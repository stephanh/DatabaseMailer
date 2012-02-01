import AssemblyKeys._ // put this at the top of the file

organization := "com.github.stephanh"

name := "DatabaseMailer"

version := "0.1"

scalaVersion := "2.9.1"

// scalacOptions += "-deprecation"

//resolvers += "Querulous" at "http://maven.twttr.com/"

externalResolvers := Seq(
  Resolver.url("proxy-ivy", url("http://localhost:8081/nexus/content/groups/public-ivy/"))(Resolver.ivyStylePatterns),
  "proxy" at "http://localhost:8081/nexus/content/groups/public/",
  "proxy-maven" at "http://localhost:8081/nexus/content/groups/public-maven/"
)

libraryDependencies ++= Seq(
  "javax.mail" % "mail" % "1.4.4",
  //"com.twitter" % "querulous" % "2.6.5",
  "com.twitter" %% "util-core" % "1.12.12",
  "org.clapper" %% "grizzled-scala" % "1.0.9",
  "org.clapper" %% "grizzled-slf4j" % "0.6.6",
  "org.fusesource.scalate" % "scalate-wikitext" % "1.5.3",
  "org.fusesource.scalate" % "scalate-page" % "1.5.3",
  "org.scala-tools.testing" %% "specs" % "1.6.9" % "test->default",
  //"ch.qos.logback" % "logback-classic" % "0.9.27",
  "org.clapper" %% "avsl" % "0.3.6",
  "commons-dbcp" % "commons-dbcp" % "1.4",
  "mysql"        % "mysql-connector-java" % "5.1.18",
  "commons-pool" % "commons-pool"         % "1.5.4"
)

seq(assemblySettings: _*)
