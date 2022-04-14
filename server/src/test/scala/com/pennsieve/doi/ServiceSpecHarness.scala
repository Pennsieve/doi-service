// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi

import akka.actor.ActorSystem
import com.pennsieve.doi.clients.{ CitationClient, DataCiteClient }
import com.pennsieve.test.AwaitableImplicits
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.exceptions.DockerException
import com.typesafe.scalalogging.StrictLogging
import com.whisk.docker.DockerFactory
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.scalatest.DockerTestKit
import org.scalatest.time.{ Second, Seconds, Span }
import org.scalatest.{ BeforeAndAfterAll, OptionValues, Suite }

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration.DurationInt

trait ServiceSpecHarness
    extends Suite
    with BeforeAndAfterAll
    with DockerPostgresService
    with DockerTestKit
    with AwaitableImplicits
    with OptionValues
    with StrictLogging { suite: Suite =>

  implicit private val system: ActorSystem = ActorSystem("doi-service")
  implicit private val executionContext: ExecutionContext = system.dispatcher

  // increase default patience to allow containers to come up
  implicit val patience: PatienceConfig =
    PatienceConfig(Span(60, Seconds), Span(1, Second))

  // provide a dockerFactory
  override implicit val dockerFactory: DockerFactory =
    try new SpotifyDockerFactory(DefaultDockerClient.fromEnv().build())
    catch {
      case _: DockerException =>
        throw new DockerException("Docker may not be running")
    }

  def getConfig(): Config =
    Config(
      host = "0.0.0.0",
      port = 8080,
      postgres = postgresConfiguration,
      dataCite = DataCiteClientConfiguration(
        username = "test",
        password = "test",
        apiUrl = "test",
        pennsievePrefix = "test"
      ),
      citation = CitationClientConfiguration(apiUrl = "test"),
      jwt = JwtConfig("test-key")
    )

  def getPorts(config: Config): Ports = new Ports(config) {
    override val dataCiteClient: DataCiteClient = new MockDataCiteClient()

    override val citationClient: CitationClient = new MockCitationClient()
  }

  lazy implicit val ports: Ports = getPorts(getConfig())

  override def beforeAll(): Unit = {
    super.beforeAll()

    val setup = isContainerReady(postgresContainer).map { _ =>
      DatabaseMigrator.run(ports.config.postgres)
    }

    Await.result(setup, 30.seconds)
  }

  override def afterAll(): Unit = {
    ports.db.close()
    super.afterAll()
  }
}
