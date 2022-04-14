import CrossCompilationUtil.{
  getScalacOptions,
  getVersion,
  handle212OnlyDependency,
  scalaVersionMatch
}

ThisBuild / organization := "com.pennsieve"

lazy val scala212 = "2.12.11"
lazy val scala213 = "2.13.8"
lazy val supportedScalaVersions = List(scala212, scala213)

ThisBuild / scalaVersion := scala212

ThisBuild / resolvers ++= Seq(
  "pennsieve-maven-proxy" at "https://nexus.pennsieve.cc/repository/maven-public"
)

ThisBuild / credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "nexus.pennsieve.cc",
  sys.env("PENNSIEVE_NEXUS_USER"),
  sys.env("PENNSIEVE_NEXUS_PW")
)

ThisBuild / version := sys.props.get("version").getOrElse("SNAPSHOT")

lazy val headerLicenseValue = Some(
  HeaderLicense.Custom(
    "Copyright (c) 2021 University of Pennsylvania. All Rights Reserved."
  )
)
lazy val headerMappingsValue = HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment

lazy val akkaCirceVersion = "0.3.0"
lazy val akkaHttpVersion = "10.1.11"
lazy val akkaVersion = "2.6.5"
lazy val circeVersion = SettingKey[String]("circeVersion")
lazy val circe212Version = "0.11.1"
lazy val circe213Version = "0.14.1"
lazy val authMiddlewareVersion = "4.2.3"
lazy val serviceUtilitiesVersion = "8-9751ee3"
lazy val utilitiesVersion = "4-55953e4"
lazy val slickVersion = "3.3.0"
lazy val slickPgVersion = "0.17.3"
lazy val dockerItVersion = "0.9.7"
lazy val logbackVersion = "1.2.3"
lazy val enumeratumVersion = SettingKey[String]("enumeratumVersion")
lazy val enumeratum212Version = "1.5.13"
lazy val enumeratum213Version = "1.7.0"
lazy val monocleVersion = "2.0.0"
lazy val discoverServiceClientVersion = "20-83899a7"

lazy val common = project
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "doi-service-common",
    headerLicense := headerLicenseValue,
    headerMappings := headerMappings.value + headerMappingsValue,
    scalacOptions := getScalacOptions(scalaVersion.value),
    crossScalaVersions := supportedScalaVersions,
    circeVersion := getVersion(
      scalaVersion.value,
      circe212Version,
      circe213Version
    ),
    enumeratumVersion := getVersion(
      scalaVersion.value,
      enumeratum212Version,
      enumeratum213Version
    ),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion.value,
      "io.circe" %% "circe-generic" % circeVersion.value,
      "io.circe" %% "circe-jawn" % circeVersion.value,
      "com.beachape" %% "enumeratum" % enumeratumVersion.value,
      "com.beachape" %% "enumeratum-circe" % enumeratumVersion.value,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion
    ),
    libraryDependencies ++= handle212OnlyDependency(
      scalaVersion.value,
      "io.circe" %% "circe-java8" % circeVersion.value
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
  .disablePlugins(ScoverageSbtPlugin)
  .dependsOn(server)
  .settings(
    name := "doi-service-scripts",
    headerLicense := headerLicenseValue,
    headerMappings := headerMappings.value + headerMappingsValue,
    scalacOptions := getScalacOptions(scalaVersion.value),
    circeVersion := getVersion(
      scalaVersion.value,
      circe212Version,
      circe213Version
    ),
    enumeratumVersion := getVersion(
      scalaVersion.value,
      enumeratum212Version,
      enumeratum213Version
    ),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion.value,
      "io.circe" %% "circe-generic" % circeVersion.value,
      "io.circe" %% "circe-jawn" % circeVersion.value,
      "com.beachape" %% "enumeratum" % enumeratumVersion.value,
      "com.beachape" %% "enumeratum-circe" % enumeratumVersion.value,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.pennsieve" %% "discover-service-client" % discoverServiceClientVersion,
      "com.pennsieve" %% "service-utilities" % serviceUtilitiesVersion
    ),
    libraryDependencies ++= handle212OnlyDependency(
      scalaVersion.value,
      "io.circe" %% "circe-java8" % circeVersion.value
    )
  )

lazy val Integration = config("integration") extend (Test)

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
    scalacOptions := getScalacOptions(scalaVersion.value),
    assembly / test := {},
    // Only run integration tests with the `integration:test` command
    inConfig(Integration)(Defaults.testTasks),
    Test / testOptions := Seq(
      Tests.Filter(!_.toLowerCase.contains("integration"))
    ),
    Integration / testOptions := Seq(
      Tests.Filter(_.toLowerCase.contains("integration"))
    ),
    libraryDependencies ++= Seq(
      "com.pennsieve" %% "service-utilities" % serviceUtilitiesVersion,
      "com.pennsieve" %% "utilities" % utilitiesVersion,
      "com.pennsieve" %% "auth-middleware" % authMiddlewareVersion,
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
      "com.github.julien-truffaut" %% "monocle-core" % monocleVersion,
      "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion,
      // Test dependencies
      "org.scalatest" %% "scalatest" % "3.0.5" % Test,
      "com.whisk" %% "docker-testkit-scalatest" % dockerItVersion % Test,
      "com.whisk" %% "docker-testkit-impl-spotify" % dockerItVersion % Test,
      "com.pennsieve" %% "utilities" % utilitiesVersion % Test classifier "tests",
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      "com.amazonaws" % "aws-java-sdk-ssm" % "1.11.714" % Test
    ),
    Compile / guardrailTasks :=
      List(
        ScalaServer(
          file("swagger/doi-service.yml"),
          pkg = "com.pennsieve.doi.server",
          modules = List("akka-http", "circe-0.11")
        )
      ),
    docker / dockerfile := {
      val artifact: File = assembly.value
      val artifactTargetPath = s"/app/${artifact.name}"
      new Dockerfile {
        from("pennsieve/java-cloudwrap:8-jre-alpine-0.5.9")
        copy(artifact, artifactTargetPath, chown = "pennsieve:pennsieve")
        copy(
          baseDirectory.value / "bin" / "run.sh",
          "/app/run.sh",
          chown = "pennsieve:pennsieve"
        )
        run(
          "wget",
          "-qO",
          "/app/newrelic.jar",
          "http://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic.jar"
        )
        run(
          "wget",
          "-qO",
          "/app/newrelic.yml",
          "http://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic.yml"
        )
        run("mkdir", "-p", "/home/pennsieve/.postgresql")
        run(
          "wget",
          "-qO",
          "/home/pennsieve/.postgresql/root.crt",
          "https://s3.amazonaws.com/rds-downloads/rds-ca-2019-root.pem"
        )
        cmd(
          "--service",
          "doi-service",
          "exec",
          "app/run.sh",
          artifactTargetPath
        )
      }
    },
    docker / imageNames := Seq(ImageName("pennsieve/doi-service:latest")),
    coverageExcludedPackages := "com.pennsieve.doi\\..*;",
    coverageMinimumStmtTotal := 0, // TODO
    coverageFailOnMinimum := true
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
    scalacOptions := getScalacOptions(scalaVersion.value),
    crossScalaVersions := supportedScalaVersions,
    publishTo := {
      val nexus = "https://nexus.pennsieve.cc/repository"
      if (isSnapshot.value) {
        Some("Nexus Realm" at s"$nexus/maven-snapshots")
      } else {
        Some("Nexus Realm" at s"$nexus/maven-releases")
      }
    },
    publishMavenStyle := true,
    Compile / guardrailTasks := scalaVersionMatch(
      scalaVersion.value,
      List(
        ScalaClient(
          file("swagger/doi-service.yml"),
          pkg = "com.pennsieve.doi.client",
          modules = List("akka-http", "circe-0.11")
        )
      ),
      List(
        ScalaClient(
          file("swagger/doi-service.yml"),
          pkg = "com.pennsieve.doi.client"
        )
      )
    )
  )

lazy val root = (project in file("."))
  .aggregate(common, server, client, scripts)
  .settings(
    // crossScalaVersions must be set to Nil on the aggregating project
    crossScalaVersions := Nil,
    publish / skip := true
  )
