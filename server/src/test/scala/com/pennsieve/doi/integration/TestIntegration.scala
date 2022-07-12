// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi.integration

import akka.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest
import com.pennsieve.auth.middleware.Jwt
import com.pennsieve.doi.Authenticator.generateServiceToken
import com.pennsieve.doi.client.doi.{
  CreateDraftDoiResponse,
  DoiClient,
  GetCitationsResponse,
  GetLatestDoiResponse,
  HideDoiResponse,
  PublishDoiResponse,
  ReviseDoiResponse
}
import com.pennsieve.doi.client.definitions._
import com.pennsieve.doi.models.{ DoiDTO, DoiState }
import com.pennsieve.doi.{ Config, Ports, ServiceSpecHarness }
import com.pennsieve.doi.handlers.DoiHandler
import com.pennsieve.test.AwaitableImplicits
import monocle.macros.syntax.lens._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{ BeforeAndAfterEach, Inside }
import org.scalatest.EitherValues._

/**
  * Integration test that runs against the DataCite test API using the non-prod
  * account credentials.
  */
class IntegrationSpec
    extends AnyWordSpec
    with ScalatestRouteTest
    with ServiceSpecHarness
    with AwaitableImplicits
    with Matchers
    with Inside
    with BeforeAndAfterEach {

  override def getConfig(): Config = {

    val ssmClient = AWSSimpleSystemsManagementClientBuilder.defaultClient()

    val dataciteUsername = ssmClient
      .getParameter(
        new GetParameterRequest()
          .withName("/dev/doi-service/datacite-client-username")
          .withWithDecryption(true)
      )
      .getParameter()
      .getValue()

    val datacitePassword = ssmClient
      .getParameter(
        new GetParameterRequest()
          .withName("/dev/doi-service/datacite-client-password")
          .withWithDecryption(true)
      )
      .getParameter()
      .getValue()

    val datacitePrefix = ssmClient
      .getParameter(
        new GetParameterRequest()
          .withName("/dev/doi-service/datacite-pennsieve-prefix")
          .withWithDecryption(true)
      )
      .getParameter()
      .getValue()

    super
      .getConfig()
      .lens(_.dataCite.username)
      .modify(_ => dataciteUsername)
      .lens(_.dataCite.password)
      .modify(_ => datacitePassword)
      .lens(_.dataCite.pennsievePrefix)
      .modify(_ => datacitePrefix)
      .lens(_.dataCite.apiUrl)
      .modify(_ => "https://api.test.datacite.org")
      .lens(_.citation.apiUrl)
      .modify(_ => "https://doi.org")
  }

  // Use real (not mock) DataCite client
  override def getPorts(config: Config): Ports = new Ports(config)

  def createRoutes(): Route =
    Route.seal(DoiHandler.routes(getPorts(getConfig())))

  def createClient(routes: Route): DoiClient =
    DoiClient.httpClient(Route.toFunction(routes))

  val client: DoiClient = createClient(createRoutes())

  "DOI service" should {
    "integrate with DataCite API" in {
      val organizationId = 0
      val datasetId = 0

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      // ----- No DOI exists --------------------------------------------------

      client
        .getLatestDoi(organizationId, datasetId, authToken)
        .awaitFinite()
        .value shouldBe an[GetLatestDoiResponse.NotFound]

      // ----- Create Draft DOI ------------------------------------------------

      val createResponse: CreateDraftDoiResponse =
        client
          .createDraftDoi(
            organizationId,
            datasetId,
            CreateDraftDoiRequest(
              title = None,
              creators = None,
              publicationYear = None,
              version = Some(1)
            ),
            authToken
          )
          .awaitFinite()
          .value

      val created: DoiDTO = createResponse
        .asInstanceOf[CreateDraftDoiResponse.Created]
        .value

      created.organizationId should be(organizationId)
      created.datasetId should be(datasetId)
      created.title should be(Some(""))
      created.url should be(None)
      created.publicationYear should be(None)
      created.state should be(Some(DoiState.Draft))
      created.creators should be(None)

      // ----- Publish a DOI -------------------------------------------------

      val publishResponse: PublishDoiResponse =
        client
          .publishDoi(
            created.doi,
            PublishDoiRequest(
              title = "Integration Test DOI",
              creators = Vector(
                CreatorDto("Jon", "Adams", Some("Q"), None) //Some("0000-0003-0837-7120")
              ),
              publicationYear = 2020,
              version = Some(1),
              description = Some(
                "This is the description of the Intergration Test DOI dataset"
              ),
              url =
                s"https://discover.pennsieve.net/test/integration/${created.doi}",
              licenses = Some(
                Vector(LicenseDto("MIT", "https://spdx.org/licenses/MIT.json"))
              ),
              externalPublications = Some(
                Vector(
                  ExternalPublicationDto(
                    "10.26275/t6j6-77pu",
                    Some("IsSourceOf")
                  )
                )
              )
            ),
            authToken
          )
          .awaitFinite()
          .value

      val published: DoiDTO = publishResponse
        .asInstanceOf[PublishDoiResponse.OK]
        .value

      published.organizationId should be(organizationId)
      published.datasetId should be(datasetId)
      published.title should be(Some("Integration Test DOI"))
      published.url should be(
        Some(s"https://discover.pennsieve.net/test/integration/${created.doi}")
      )
      published.publicationYear should be(Some(2020))
      published.state should be(Some(DoiState.Findable))
      published.creators should be(Some(List(Some("Jon Q Adams"))))

      // ----- Revise a DOI ---------------------------------------------------

      val reviseResponse: ReviseDoiResponse =
        client
          .reviseDoi(
            created.doi,
            ReviseDoiRequest(
              title = "Revised DOI",
              creators = Vector(
                CreatorDto(
                  "Jon",
                  "Adams",
                  Some("Q"),
                  Some("0000-0003-0837-7120")
                )
              ),
              version = Some(1),
              externalPublications = Some(
                Vector(
                  ExternalPublicationDto(
                    "10.26275/v62f-qd4v",
                    Some("IsSourceOf")
                  )
                )
              )
            ),
            authToken
          )
          .awaitFinite()
          .value

      reviseResponse shouldBe an[ReviseDoiResponse.OK]

      val revised = client
        .getLatestDoi(organizationId, datasetId, authToken)
        .awaitFinite()
        .value
        .asInstanceOf[GetLatestDoiResponse.OK]
        .value

      revised.organizationId should be(organizationId)
      revised.datasetId should be(datasetId)
      revised.title should be(Some("Revised DOI"))
      revised.url should be(
        Some(s"https://discover.pennsieve.net/test/integration/${created.doi}")
      )
      revised.publicationYear should be(Some(2020))
      revised.state should be(Some(DoiState.Findable))
      revised.creators should be(Some(List(Some("Jon Q Adams"))))

      // ----- Unpublish a DOI ------------------------------------------------

      val hideResponse: HideDoiResponse =
        client
          .hideDoi(created.doi, authToken)
          .awaitFinite()
          .value

      hideResponse shouldBe an[HideDoiResponse.OK]

      val hidden = client
        .getLatestDoi(organizationId, datasetId, authToken)
        .awaitFinite()
        .value
        .asInstanceOf[GetLatestDoiResponse.OK]
        .value

      hidden.organizationId should be(organizationId)
      hidden.datasetId should be(datasetId)
      hidden.title should be(Some("Revised DOI"))
      hidden.url should be(
        Some(s"https://discover.pennsieve.net/test/integration/${created.doi}")
      )
      hidden.publicationYear should be(Some(2020))
      hidden.state should be(Some(DoiState.Registered))
      hidden.creators should be(Some(List(Some("Jon Q Adams"))))
    }

    "get citations from CrossCite" in {
      val organizationId = 0
      val datasetId = 0

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      val citation = client
        .getCitations(
          List("10.1073/pnas.74.12.5463", "10.1073/MISSING-DOI"),
          authToken
        )
        .awaitFinite()
        .value
        .asInstanceOf[GetCitationsResponse.MultiStatus]
        .value

      citation should contain theSameElementsAs List(
        CitationDto(
          status = 200,
          doi = "10.1073/pnas.74.12.5463",
          citation = Some(
            "Sanger, F., Nicklen, S., & Coulson, A. R. (1977). DNA sequencing with chain-terminating inhibitors. Proceedings of the National Academy of Sciences, 74(12), 5463–5467. https://doi.org/10.1073/pnas.74.12.5463"
          )
        ),
        CitationDto(status = 404, doi = "10.1073/MISSING-DOI", citation = None)
      )
    }
  }
}
