addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.0.0-M3")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.7.3")

externalResolvers := Seq(
  Resolver.url("proxy-ivy", url("http://localhost:8081/nexus/content/groups/public-ivy/"))(Resolver.ivyStylePatterns),
  "proxy" at "http://localhost:8081/nexus/content/groups/public/",
  "proxy-maven" at "http://localhost:8081/nexus/content/groups/public-maven/")

//resolvers += Resolver.url("sbt-plugin-releases",
//  new URL("http://localhost:8081/nexus/content/groups/public/"))(Resolver.ivyStylePatterns)

