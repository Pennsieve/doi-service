// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.blackfynn.doi

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.blackfynn.auth.middleware.Jwt
import com.blackfynn.doi.clients.{
  CitationClient,
  CitationClientImpl,
  DataCiteClient,
  DataCiteClientImpl
}
import com.blackfynn.doi.db.profile.api._
import com.blackfynn.service.utilities.ContextLogger
import com.zaxxer.hikari.HikariDataSource
import slick.util.AsyncExecutor

import scala.concurrent.ExecutionContext

class Ports(
  val config: Config
)(implicit
  system: ActorSystem,
  executionContext: ExecutionContext,
  materializer: ActorMaterializer
) {

  val jwt: Jwt.Config = new Jwt.Config {
    val key: String = config.jwt.key
  }

  val db: Database = {
    val hikariDataSource = new HikariDataSource()

    hikariDataSource.setJdbcUrl(config.postgres.jdbcURL)
    hikariDataSource.setUsername(config.postgres.user)
    hikariDataSource.setPassword(config.postgres.password)
    hikariDataSource.setMaximumPoolSize(config.postgres.numConnections)
    hikariDataSource.setDriverClassName(config.postgres.driver)

    // Currently minThreads, maxThreads and maxConnections MUST be the same value
    // https://github.com/slick/slick/issues/1938
    Database.forDataSource(
      hikariDataSource,
      maxConnections = None, // Ignored if an executor is provided
      executor = AsyncExecutor(
        name = "AsyncExecutor.pennsieve",
        minThreads = config.postgres.numConnections,
        maxThreads = config.postgres.numConnections,
        maxConnections = config.postgres.numConnections,
        queueSize = config.postgres.queueSize
      )
    )
  }

  val dataCiteClient: DataCiteClient =
    new DataCiteClientImpl(Http(), config.dataCite)

  val citationClient: CitationClient =
    new CitationClientImpl(Http(), config.citation)

  val logger = new ContextLogger()
  val log = logger.context
}
