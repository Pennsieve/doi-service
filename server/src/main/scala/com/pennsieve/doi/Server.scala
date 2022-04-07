// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import com.pennsieve.doi.handlers.{ DoiHandler, HealthcheckHandler }
import com.pennsieve.service.utilities.MigrationRunner
import com.typesafe.scalalogging.StrictLogging
import pureconfig.generic.auto._

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext }

object DatabaseMigrator extends StrictLogging {

  def run(configuration: PostgresConfiguration): Unit = {
    val migrator =
      new MigrationRunner(
        configuration.jdbcURL,
        configuration.user,
        configuration.password,
        schema = Some(db.schema)
      )

    val (count, _) = migrator.run()
    logger.info(
      s"Successfully ran $count migrations on ${configuration.jdbcURL}"
    )
  }
}

object Server extends App with StrictLogging {
  val config: Config = pureconfig.loadConfigOrThrow[Config]

  implicit val system: ActorSystem = ActorSystem("doi-service")
  implicit val executionContext: ExecutionContext = system.dispatcher

  implicit val ports: Ports = new Ports(config)

  DatabaseMigrator.run(config.postgres)

  val routes: Route =
    Route.seal(HealthcheckHandler.routes ~ DoiHandler.routes(ports))

  Http().bindAndHandle(routes, config.host, config.port)

  logger.info(s"Server online at http://${config.host}:${config.port}")

  Await.result(system.whenTerminated, Duration.Inf)
}
