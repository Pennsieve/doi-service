// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.blackfynn.doi.integration

import akka.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest
import com.blackfynn.auth.middleware.Jwt
import com.blackfynn.doi.Authenticator.generateServiceToken
import com.blackfynn.doi.client.doi.{
  CreateDraftDoiResponse,
  DoiClient,
  GetCitationsResponse,
  GetDoiResponse,
  GetLatestDoiResponse,
  HideDoiResponse,
  PublishDoiResponse,
  ReviseDoiResponse
}
import com.blackfynn.doi.client.definitions._
import com.blackfynn.doi.clients.{ DataCiteClient, DataCiteClientImpl }
import com.blackfynn.doi.db.DoiMapper
import com.blackfynn.doi.models.{
  Creator,
  DataciteDoi,
  Doi,
  DoiDTO,
  DoiEvent,
  DoiState,
  RelationType
}
import com.blackfynn.doi.{
  Config,
  DataCiteClientConfiguration,
  JwtConfig,
  Ports,
  ServiceSpecHarness,
  TestUtilities
}
import com.blackfynn.doi.handlers.DoiHandler
import com.blackfynn.test.AwaitableImplicits
import monocle.macros.syntax.lens._
import io.circe.syntax._
import org.scalatest.{ BeforeAndAfterEach, Inside, Matchers, WordSpec }

/**
  * Integration test that runs against the DataCite test API using the non-prod
  * account credentials.
  */
class IntegrationSpec
    extends WordSpec
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

    super.getConfig
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
    DoiClient.httpClient(Route.asyncHandler(routes))

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
        .right
        .get shouldBe an[GetLatestDoiResponse.NotFound]

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
          .right
          .get

      val created: DoiDTO = createResponse
        .asInstanceOf[CreateDraftDoiResponse.Created]
        .value
        .as[DoiDTO]
        .right
        .get

      created should have(
        'organizationId (organizationId),
        'datasetId (datasetId),
        'title (Some("")), // TODO collapse this
        'url (None),
        'publicationYear (None),
        'state (Some(DoiState.Draft)),
        'creators (None)
      )

      // ----- Publish a DOI -------------------------------------------------

      val publishResponse: PublishDoiResponse =
        client
          .publishDoi(
            created.doi,
            PublishDoiRequest(
              title = "Integration Test DOI",
              creators = IndexedSeq(
                CreatorDTO("Jon", "Adams", Some("Q"), None) //Some("0000-0003-0837-7120")
              ),
              publicationYear = 2020,
              version = Some(1),
              description = Some(
                "This is the description of the Intergration Test DOI dataset"
              ),
              url =
                s"https://discover.pennsieve.net/test/integration/${created.doi}",
              licenses = Some(
                IndexedSeq(
                  LicenseDTO("MIT", "https://spdx.org/licenses/MIT.json")
                )
              ),
              externalPublications = Some(
                IndexedSeq(
                  ExternalPublicationDTO(
                    "10.26275/t6j6-77pu",
                    Some("IsSourceOf")
                  )
                )
              )
            ),
            authToken
          )
          .awaitFinite()
          .right
          .get

      val published: DoiDTO = publishResponse
        .asInstanceOf[PublishDoiResponse.OK]
        .value
        .as[DoiDTO]
        .right
        .get

      published should have(
        'organizationId (organizationId),
        'datasetId (datasetId),
        'title (Some("Integration Test DOI")),
        'url (
          Some(
            s"https://discover.pennsieve.net/test/integration/${created.doi}"
          )
        ),
        'publicationYear (Some(2020)),
        'state (Some(DoiState.Findable)),
        'creators (Some(List(Some("Jon Q Adams"))))
      )

      // ----- Revise a DOI ---------------------------------------------------

      val reviseResponse: ReviseDoiResponse =
        client
          .reviseDoi(
            created.doi,
            ReviseDoiRequest(
              title = "Revised DOI",
              creators = IndexedSeq(
                CreatorDTO(
                  "Jon",
                  "Adams",
                  Some("Q"),
                  Some("0000-0003-0837-7120")
                )
              ),
              version = Some(1),
              externalPublications = Some(
                IndexedSeq(
                  ExternalPublicationDTO(
                    "10.26275/v62f-qd4v",
                    Some("IsSourceOf")
                  )
                )
              )
            ),
            authToken
          )
          .awaitFinite()
          .right
          .get

      reviseResponse shouldBe an[ReviseDoiResponse.OK]

      val revised = client
        .getLatestDoi(organizationId, datasetId, authToken)
        .awaitFinite()
        .right
        .get
        .asInstanceOf[GetLatestDoiResponse.OK]
        .value
        .as[DoiDTO]
        .right
        .get

      revised should have(
        'organizationId (organizationId),
        'datasetId (datasetId),
        'title (Some("Revised DOI")),
        'url (
          Some(
            s"https://discover.pennsieve.net/test/integration/${created.doi}"
          )
        ),
        'publicationYear (Some(2020)),
        'state (Some(DoiState.Findable)),
        'creators (Some(List(Some("Jon Q Adams"))))
      )

      // ----- Unpublish a DOI ------------------------------------------------

      val hideResponse: HideDoiResponse =
        client
          .hideDoi(created.doi, authToken)
          .awaitFinite()
          .right
          .get

      hideResponse shouldBe an[HideDoiResponse.OK]

      val hidden = client
        .getLatestDoi(organizationId, datasetId, authToken)
        .awaitFinite()
        .right
        .get
        .asInstanceOf[GetLatestDoiResponse.OK]
        .value
        .as[DoiDTO]
        .right
        .get

      hidden should have(
        'organizationId (organizationId),
        'datasetId (datasetId),
        'title (Some("Revised DOI")),
        'url (
          Some(
            s"https://discover.pennsieve.net/test/integration/${created.doi}"
          )
        ),
        'publicationYear (Some(2020)),
        'state (Some(DoiState.Registered)),
        'creators (Some(List(Some("Jon Q Adams"))))
      )
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
        .right
        .get
        .asInstanceOf[GetCitationsResponse.MultiStatus]
        .value

      citation should contain theSameElementsAs List(
        CitationDTO(
          status = 200,
          doi = "10.1073/pnas.74.12.5463",
          citation = Some(
            "Sanger, F., Nicklen, S., & Coulson, A. R. (1977). DNA sequencing with chain-terminating inhibitors. Proceedings of the National Academy of Sciences, 74(12), 5463–5467. https://doi.org/10.1073/pnas.74.12.5463"
          )
        ),
        CitationDTO(status = 404, doi = "10.1073/MISSING-DOI", citation = None)
      )
    }
  }
}
