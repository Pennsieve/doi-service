ThisBuild / organization := "com.blackfynn"
ThisBuild / scalaVersion := "2.12.11"
ThisBuild / scalacOptions ++= Seq(
  "-encoding", "utf-8",
  "-deprecation",
  "-explaintypes",
  "-feature",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-Ypartial-unification",
  "-Ywarn-infer-any",
)

ThisBuild / resolvers ++= Seq(
  "pennsieve-maven-proxy" at "https://nexus.pennsieve.cc/repository/maven-public",
  Resolver.url("pennsieve-ivy-proxy", url("https://nexus.pennsieve.cc/repository/ivy-public/"))( Patterns("[organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]") ),
  Resolver.jcenterRepo,
  Resolver.bintrayRepo("commercetools", "maven")
)

ThisBuild / credentials += Credentials("Sonatype Nexus Repository Manager",
  "nexus.pennsieve.cc",
  sys.env("PENNSIEVE_NEXUS_USER"),
  sys.env("PENNSIEVE_NEXUS_PW")
)

ThisBuild / version := sys.props.get("version").getOrElse("SNAPSHOT")

lazy val headerLicenseValue = Some(HeaderLicense.Custom(
  "Copyright (c) 2021 University of Pennsylvania. All Rights Reserved."
))
lazy val headerMappingsValue = HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment

lazy val akkaCirceVersion        = "0.3.0"
lazy val akkaHttpVersion         = "10.1.11"
lazy val akkaVersion             = "2.6.5"
lazy val circeVersion            = "0.11.0"
lazy val authMiddlewareVersion   = "4.2.2"
lazy val serviceUtilitiesVersion = "1.3.4-SNAPSHOT"
lazy val utilitiesVersion        = "0.1.10-SNAPSHOT"
lazy val slickVersion            = "3.3.0"
lazy val slickPgVersion          = "0.17.3"
lazy val dockerItVersion         = "0.9.7"
lazy val logbackVersion          = "1.2.3"
lazy val enumeratumVersion       = "1.5.13"
lazy val monocleVersion          = "2.0.0"
lazy val discoverServiceClientVersion = "355-b2641e1"


lazy val common = project
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "doi-service-common",
    headerLicense := headerLicenseValue,
    headerMappings := headerMappings.value + headerMappingsValue,
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-jawn" % circeVersion,
      "io.circe" %% "circe-java8" % circeVersion,
      "com.beachape" %% "enumeratum" % enumeratumVersion,
      "com.beachape" %% "enumeratum-circe" % enumeratumVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion
    ),
    publishTo := {
      val nexus = "https://nexus.pennsieve.cc/repository"
      if (isSnapshot.value) {
        Some("Nexus Realm" at s"$nexus/maven-snapshots")
      } else {
        Some("Nexus Realm" at s"$nexus/maven-releases")
      }
    },
    publishMavenStyle := true
  )

lazy val scripts = project
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(server)
  .settings(
    name := "doi-service-scripts",
    headerLicense := headerLicenseValue,
    headerMappings := headerMappings.value + headerMappingsValue,
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-jawn" % circeVersion,
      "io.circe" %% "circe-java8" % circeVersion,
      "com.beachape" %% "enumeratum" % enumeratumVersion,
      "com.beachape" %% "enumeratum-circe" % enumeratumVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.blackfynn" %% "discover-service-client" % discoverServiceClientVersion,
      "com.blackfynn" %% "service-utilities" % serviceUtilitiesVersion,

    )
  )

lazy val Integration = config("integration") extend(Test)

lazy val server = project
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(DockerPlugin)
  .dependsOn(common)
  .configs(Integration)
  .settings(
    name := "doi-service",
    headerLicense := headerLicenseValue,
    headerMappings := headerMappings.value + headerMappingsValue,
    scalafmtOnCompile := true,
    test in assembly := {},

    // Only run integration tests with the `integration:test` command
    inConfig(Integration)(Defaults.testTasks),
    Test / testOptions := Seq(Tests.Filter(! _.toLowerCase.contains("integration"))),
    Integration / testOptions := Seq(Tests.Filter(_.toLowerCase.contains("integration"))),

      libraryDependencies ++= Seq(
      "com.blackfynn" %% "service-utilities" % serviceUtilitiesVersion,
      "com.blackfynn" %% "utilities" % utilitiesVersion,
      "com.blackfynn" %% "auth-middleware" % authMiddlewareVersion,

      "com.github.pureconfig" %% "pureconfig" % "0.10.2",
      "com.iheart" %% "ficus" % "1.4.0",

      "com.typesafe.slick" %% "slick" % slickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
      "com.github.tminglei" %% "slick-pg" % slickPgVersion,
      "com.github.tminglei" %% "slick-pg_circe-json" % slickPgVersion,

      "io.scalaland" %% "chimney" % "0.2.1",
      "org.mdedetrich" %% "akka-http-circe" % akkaCirceVersion,
      "org.postgresql" % "postgresql" % "42.2.4",

      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "ch.qos.logback" % "logback-core" % logbackVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
      "net.logstash.logback" % "logstash-logback-encoder" % "5.2",

      "com.github.julien-truffaut" %%  "monocle-core"  % monocleVersion,
      "com.github.julien-truffaut" %%  "monocle-macro" % monocleVersion,

      // Test dependencies
      "org.scalatest" %% "scalatest"% "3.0.5" % Test,
      "com.whisk" %% "docker-testkit-scalatest" % dockerItVersion % Test,
      "com.whisk" %% "docker-testkit-impl-spotify" % dockerItVersion % Test,
      "com.blackfynn" %% "utilities" % utilitiesVersion % Test classifier "tests",
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      "com.amazonaws" % "aws-java-sdk-ssm" % "1.11.714" % Test,
    ),
    guardrailTasks in Compile := List(
      Server(file("swagger/doi-service.yml"), pkg="com.blackfynn.doi.server")
    ),
    dockerfile in docker := {
      val artifact: File = assembly.value
      val artifactTargetPath = s"/app/${artifact.name}"
      new Dockerfile {
        from("pennsieve/java-cloudwrap:8-jre-alpine-0.5.9")
        copy(artifact, artifactTargetPath, chown="pennsieve:pennsieve")
        copy(baseDirectory.value / "bin" / "run.sh", "/app/run.sh", chown="pennsieve:pennsieve")
        run("wget", "-qO", "/app/newrelic.jar", "http://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic.jar")
        run("wget", "-qO", "/app/newrelic.yml", "http://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic.yml")
        run("mkdir", "-p", "/home/pennsieve/.postgresql")
        run(
          "wget",
          "-qO",
          "/home/pennsieve/.postgresql/root.crt",
          "https://s3.amazonaws.com/rds-downloads/rds-ca-2019-root.pem"
        )
        cmd("--service", "doi-service", "exec", "app/run.sh", artifactTargetPath)
      }
    },
    imageNames in docker := Seq(
      ImageName("pennsieve/doi-service:latest")
    ),
    coverageExcludedPackages := "com.blackfynn.graphview.client\\..*;",
    coverageMinimum := 0, // TODO
    coverageFailOnMinimum := true,
  )
  .dependsOn(client % "test->compile;compile->compile")
  .dependsOn(common % "test->compile;compile->compile")


lazy val client = project
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(common)
  .settings(
    name := "doi-service-client",
    headerLicense := headerLicenseValue,
    headerMappings := headerMappings.value + headerMappingsValue,
    publishTo := {
      val nexus = "https://nexus.pennsieve.cc/repository"
      if (isSnapshot.value) {
        Some("Nexus Realm" at s"$nexus/maven-snapshots")
      } else {
        Some("Nexus Realm" at s"$nexus/maven-releases")
      }
    },
    publishMavenStyle := true,
    guardrailTasks in Compile := List(
      Client(file("./swagger/doi-service.yml"), pkg="com.blackfynn.doi.client")
    ),
  )


lazy val root = (project in file("."))
  .aggregate(common, server, client)
