resolvers ++= Seq(
  "pennsieve-maven-proxy" at "https://nexus.pennsieve.cc/repository/maven-public",
  Resolver.url("pennsieve-ivy-proxy", url("https://nexus.pennsieve.cc/repository/ivy-public/"))( Patterns("[organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]") ),
)

credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "nexus.pennsieve.cc",
  sys.env.getOrElse("PENNSIEVE_NEXUS_USER", "pennsieveci"),
  sys.env.getOrElse("PENNSIEVE_NEXUS_PW", "")
)

addSbtPlugin("com.twilio" % "sbt-guardrail" % "0.43.0")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.5.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")

addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.6.0-RC4")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.0.0")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")
