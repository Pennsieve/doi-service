// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

case class Config(
  host: String = "0.0.0.0",
  port: Int = 8080,
  postgres: PostgresConfiguration,
  dataCite: DataCiteClientConfiguration,
  jwt: JwtConfig,
  citation: CitationClientConfiguration
)

case class PostgresConfiguration(
  host: String,
  port: Int,
  database: String,
  user: String,
  password: String,
  numConnections: Int = 20,
  queueSize: Int = 1000,
  driver: String = "org.postgresql.Driver",
  useSSL: Boolean = true
) {
  private val jdbcBaseURL: String = s"jdbc:postgresql://$host:$port/$database"
  final val jdbcURL = {
    if (useSSL) jdbcBaseURL + "?ssl=true&sslmode=verify-ca"
    else jdbcBaseURL
  }
}

case class DataCiteClientConfiguration(
  username: String,
  password: String,
  apiUrl: String,
  pennsievePrefix: String
)

case class CitationClientConfiguration(apiUrl: String)

case class JwtConfig(key: String, duration: FiniteDuration = 5.minutes)
