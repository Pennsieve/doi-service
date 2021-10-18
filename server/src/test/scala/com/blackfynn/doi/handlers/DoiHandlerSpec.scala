// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi.handlers

import akka.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.pennsieve.auth.middleware.Jwt
import com.pennsieve.doi.Authenticator.generateServiceToken
import com.pennsieve.doi.client.definitions._
import com.pennsieve.doi.db.profile.api._
import io.circe.parser.decode
import com.pennsieve.doi.client.doi.{
  CreateDraftDoiResponse,
  DoiClient,
  GetCitationsResponse,
  GetDoiResponse,
  GetLatestDoiResponse,
  HideDoiResponse,
  PublishDoiResponse,
  ReviseDoiResponse
}
import com.pennsieve.doi.db.{ CitationCacheMapper, DoiMapper }
import com.pennsieve.doi.models.{
  Contributor,
  Creator,
  DataciteDoi,
  Description,
  Doi,
  DoiAttributes,
  DoiDTO,
  DoiData,
  DoiDate,
  DoiEvent,
  DoiState,
  NameIdentifier,
  RelatedIdentifier,
  RelationType,
  Rights,
  Title,
  Type
}
import com.pennsieve.doi.{ ServiceSpecHarness, TestUtilities }
import com.pennsieve.test.AwaitableImplicits
import io.circe.syntax._
import org.scalatest.{ BeforeAndAfterEach, Matchers, WordSpec }
import java.time.{ OffsetDateTime, ZoneId }

class DoiHandlerSpec
    extends WordSpec
    with ScalatestRouteTest
    with ServiceSpecHarness
    with AwaitableImplicits
    with Matchers
    with BeforeAndAfterEach {

  def createRoutes(): Route =
    Route.seal(DoiHandler.routes(getPorts(getConfig())))

  def createClient(routes: Route): DoiClient =
    DoiClient.httpClient(Route.asyncHandler(routes))

  val client: DoiClient = createClient(createRoutes())

  "GET /organizations/:organizationId/datasets/:datasetId/doi" should {

    "return the latest dataset DOI from datacite" in {
      val organizationId = 2
      val datasetId = 5

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      val internalDoi =
        TestUtilities.createDoi(ports.db)(organizationId, datasetId)

      val expectedDoi = ports.db
        .run(
          DoiMapper
            .getDoi(internalDoi.doi)
        )
        .await

      val response: GetLatestDoiResponse =
        client
          .getLatestDoi(organizationId, datasetId, authToken)
          .awaitFinite()
          .right
          .get

      val expected: DoiDTO =
        DoiDTO.apply(expectedDoi, TestUtilities.testDoi)

      response shouldBe GetLatestDoiResponse.OK(expected.asJson)

    }

    "return Forbidden when not authorized to access the given organization" in {
      val organizationId = 2
      val datasetId = 5

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      TestUtilities.createDoi(ports.db)(organizationId, datasetId)

      val differentOrganizationId = organizationId + 1

      val response: GetLatestDoiResponse =
        client
          .getLatestDoi(differentOrganizationId, datasetId, authToken)
          .awaitFinite()
          .right
          .get

      response shouldBe GetLatestDoiResponse.Forbidden(
        s"Not allowed for organization $differentOrganizationId"
      )

    }

    "return Forbidden when not authorized to access the given dataset" in {
      val organizationId = 2
      val datasetId = 5

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      TestUtilities.createDoi(ports.db)(organizationId, datasetId)

      val differentDatasetId = datasetId + 1

      val response: GetLatestDoiResponse =
        client
          .getLatestDoi(organizationId, differentDatasetId, authToken)
          .awaitFinite()
          .right
          .get

      response shouldBe GetLatestDoiResponse.Forbidden(
        s"Not allowed for dataset $differentDatasetId"
      )

    }

    "return NotFound when the dataset has no DOI" in {
      val organizationId = 5
      val datasetId = 100

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      val response: GetLatestDoiResponse =
        client
          .getLatestDoi(organizationId, datasetId, authToken)
          .awaitFinite()
          .right
          .get

      response shouldBe GetLatestDoiResponse.NotFound(
        s"doi for organizationId=$organizationId datasetId=$datasetId"
      )

    }
  }

  "POST /organizations/:organizationId/datasets/:datasetId/doi" should {

    "create a Draft DOI for the dataset" in {
      val organizationId = 2
      val datasetId = 5

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      val request = com.pennsieve.doi.client.definitions.CreateDraftDoiRequest(
        title = Some("this is a test"),
        creators = Some(IndexedSeq(CreatorDTO("Jon", "Adams", Some("Q")))),
        publicationYear = Some(2019),
        version = Some(1)
      )

      val response: CreateDraftDoiResponse =
        client
          .createDraftDoi(organizationId, datasetId, request, authToken)
          .awaitFinite()
          .right
          .get

      val expectedDoi: Doi = ports.db
        .run(
          DoiMapper
            .getLatestDatasetDoi(organizationId, datasetId)
        )
        .await

      val expectedResponse: DoiDTO =
        DoiDTO.apply(expectedDoi, TestUtilities.testDoi)

      response shouldBe CreateDraftDoiResponse.Created(expectedResponse.asJson)

    }

    "return Forbidden when not authorized to access the given organization" in {
      val organizationId = 2
      val datasetId = 5

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      val request = com.pennsieve.doi.client.definitions.CreateDraftDoiRequest(
        title = Some("this is a test"),
        creators = Some(IndexedSeq(CreatorDTO("Jon", "Adams", Some("Q")))),
        publicationYear = Some(2019),
        version = Some(1)
      )

      val differentOrganizationId = organizationId + 1

      val response: CreateDraftDoiResponse =
        client
          .createDraftDoi(
            differentOrganizationId,
            datasetId,
            request,
            authToken
          )
          .awaitFinite()
          .right
          .get

      response shouldBe CreateDraftDoiResponse.Forbidden(
        s"Not allowed for organization $differentOrganizationId"
      )

    }

    "return Forbidden when not authorized to access the given dataset" in {
      val organizationId = 2
      val datasetId = 5

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      val request = com.pennsieve.doi.client.definitions.CreateDraftDoiRequest(
        title = Some("this is a test"),
        creators = Some(IndexedSeq(CreatorDTO("Jon", "Adams", Some("Q")))),
        publicationYear = Some(2019),
        version = Some(1)
      )

      val differentDatasetId = datasetId + 1

      val response: CreateDraftDoiResponse =
        client
          .createDraftDoi(
            organizationId,
            differentDatasetId,
            request,
            authToken
          )
          .awaitFinite()
          .right
          .get

      response shouldBe CreateDraftDoiResponse.Forbidden(
        s"Not allowed for dataset $differentDatasetId"
      )

    }

    "return Bad Request when the requested DOI is not available" in {
      val organizationId = 2
      val datasetId = 5

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      TestUtilities.createDoi(ports.db)(doi = "test/abcd-1234")

      val request = com.pennsieve.doi.client.definitions.CreateDraftDoiRequest(
        title = Some("this is a test"),
        creators = Some(IndexedSeq(CreatorDTO("Jon", "Adams", Some("Q")))),
        publicationYear = Some(2019),
        suffix = Some("abcd-1234"),
        version = Some(1)
      )

      val response: CreateDraftDoiResponse =
        client
          .createDraftDoi(organizationId, datasetId, request, authToken)
          .awaitFinite()
          .right
          .get

      response shouldBe CreateDraftDoiResponse.BadRequest(
        s"The requested doi is already in use"
      )

    }
  }

  "GET /doi/:id" should {
    "return a DOI" in {
      val organizationId = 2
      val datasetId = 5

      val internalDoi =
        TestUtilities.createDoi(ports.db)(organizationId, datasetId)

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      val response =
        client
          .getDoi(internalDoi.doi, authToken)
          .awaitFinite()
          .right
          .get

      val expected: DoiDTO =
        DoiDTO.apply(internalDoi, TestUtilities.testDoi)

      response shouldBe GetDoiResponse.OK(expected.asJson)
    }

    "return Forbidden when not authorized to access the given dataset" in {
      val organizationId = 2
      val datasetId = 5

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val differentDatasetId = datasetId + 1

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      val internalDoi =
        TestUtilities.createDoi(ports.db)(organizationId, differentDatasetId)

      val response: GetDoiResponse =
        client
          .getDoi(internalDoi.doi, authToken)
          .awaitFinite()
          .right
          .get

      response shouldBe GetDoiResponse.Forbidden(
        s"Not allowed for dataset $differentDatasetId"
      )
    }

    "return NotFound when the requested DOI does not exist" in {
      val organizationId = 2
      val datasetId = 5

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      val response: GetDoiResponse =
        client
          .getDoi("12345", authToken)
          .awaitFinite()
          .right
          .get

      response shouldBe GetDoiResponse.NotFound("DOI 12345 could not be found")
    }
  }

  "PUT /doi/:id/publish" should {

    "Select Pennsieve Discover as default Publisher when not specifically defined" in {
      val organizationId = 2
      val datasetId = 5

      val internalDoi =
        TestUtilities.createDoi(ports.db)(organizationId, datasetId)

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      val body = com.pennsieve.doi.client.definitions.PublishDoiRequest(
        title = "this is a test without identified publisher",
        creators = IndexedSeq(
          CreatorDTO("Salvatore", "Bonno", Some("P")),
          CreatorDTO("Cherilyn", "Sarkisian")
        ),
        publicationYear = 2019,
        url = "https://www.url.com",
        version = Some(1),
        licenses = Some(
          IndexedSeq(
            LicenseDTO(
              "Apache 2.0",
              "https://spdx.org/licenses/Apache-2.0.json"
            )
          )
        ),
        owner = Some(CreatorDTO("Cherilyn", "Sarkisian")),
        description = Some("Description of the dataset"),
        collections = Some(IndexedSeq(CollectionDTO("Collection Title", 1))),
        externalPublications = Some(
          IndexedSeq(
            ExternalPublicationDTO("10.1117/12.911373", Some("IsSourceOf"))
          )
        )
      )

      val response =
        client
          .publishDoi(internalDoi.doi, body, authToken)
          .awaitFinite()
          .right
          .get

      val expectedDataciteDoi = DataciteDoi(
        internalDoi.doi,
        List(
          Creator("Salvatore", "Bonno", Some("P"), None),
          Creator("Cherilyn", "Sarkisian", None, None)
        ),
        "this is a test without identified publisher",
        Some(2019),
        state = DoiState.Findable,
        url = Some("https://www.url.com"),
        publisher = Some("Pennsieve Discover"),
        version = Some(1),
        descriptions = Some(
          List(
            Description("Description of the dataset"),
            Description("Collection Title", "SeriesInformation")
          )
        ),
        rightsList = List(
          Rights(
            "Apache 2.0",
            Some("https://spdx.org/licenses/Apache-2.0.json")
          )
        ),
        relatedIdentifiers = List(
          RelatedIdentifier(
            relatedIdentifier = "10.1117/12.911373",
            relationType = RelationType.Describes
          )
        ),
        owner = Some(Contributor("Cherilyn", "Sarkisian", None))
      )

      val expectedResponse: DoiDTO =
        DoiDTO.apply(internalDoi, expectedDataciteDoi)

      response shouldBe PublishDoiResponse.OK(expectedResponse.asJson)
      expectedResponse.publisher
    }

    "mark a DOI as Findable" in {
      val organizationId = 2
      val datasetId = 5

      val internalDoi =
        TestUtilities.createDoi(ports.db)(organizationId, datasetId)

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      val body = com.pennsieve.doi.client.definitions.PublishDoiRequest(
        title = "this is a test",
        creators = IndexedSeq(
          CreatorDTO("Salvatore", "Bonno", Some("P")),
          CreatorDTO("Cherilyn", "Sarkisian")
        ),
        publicationYear = 2019,
        url = "https://www.url.com",
        version = Some(1),
        licenses = Some(
          IndexedSeq(
            LicenseDTO(
              "Apache 2.0",
              "https://spdx.org/licenses/Apache-2.0.json"
            )
          )
        ),
        owner = Some(CreatorDTO("Cherilyn", "Sarkisian")),
        description = Some("Description of the dataset"),
        publisher = Some("Random Organization"),
        collections = Some(IndexedSeq(CollectionDTO("Collection Title", 1))),
        externalPublications = Some(
          IndexedSeq(
            ExternalPublicationDTO("10.1117/12.911373", Some("IsSourceOf"))
          )
        )
      )

      val response =
        client
          .publishDoi(internalDoi.doi, body, authToken)
          .awaitFinite()
          .right
          .get

      val expectedDataciteDoi = DataciteDoi(
        internalDoi.doi,
        List(
          Creator("Salvatore", "Bonno", Some("P"), None),
          Creator("Cherilyn", "Sarkisian", None, None)
        ),
        "this is a test",
        Some(2019),
        state = DoiState.Findable,
        url = Some("https://www.url.com"),
        publisher = Some("Random Organization"),
        version = Some(1),
        descriptions = Some(
          List(
            Description("Description of the dataset"),
            Description("Collection Title", "SeriesInformation")
          )
        ),
        rightsList = List(
          Rights(
            "Apache 2.0",
            Some("https://spdx.org/licenses/Apache-2.0.json")
          )
        ),
        relatedIdentifiers = List(
          RelatedIdentifier(
            relatedIdentifier = "10.1117/12.911373",
            relationType = RelationType.Describes
          )
        ),
        owner = Some(Contributor("Cherilyn", "Sarkisian", None))
      )

      val expectedResponse: DoiDTO =
        DoiDTO.apply(internalDoi, expectedDataciteDoi)

      response shouldBe PublishDoiResponse.OK(expectedResponse.asJson)
      expectedResponse.publisher
    }
  }

  "PUT /doi/:id/revise" should {
    "update a previously Findable DOI" in {
      val organizationId = 2
      val datasetId = 5

      val internalDoi =
        TestUtilities.createDoi(ports.db)(organizationId, datasetId)

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      val body = com.pennsieve.doi.client.definitions.PublishDoiRequest(
        title = "this is a test",
        creators = IndexedSeq(
          CreatorDTO("Salvatore", "Bonno", Some("P")),
          CreatorDTO("Cherilyn", "Sarkisian")
        ),
        publicationYear = 2019,
        url = "https://www.url.com",
        version = Some(1),
        licenses = Some(
          IndexedSeq(
            LicenseDTO(
              "Apache 2.0",
              "https://spdx.org/licenses/Apache-2.0.json"
            )
          )
        ),
        owner = Some(CreatorDTO("Cherilyn", "Sarkisian")),
        externalPublications = Some(
          IndexedSeq(
            ExternalPublicationDTO("10.1117/12.911373", Some("IsReferencedBy"))
          )
        )
      )

      client
        .publishDoi(internalDoi.doi, body, authToken)
        .awaitFinite()
        .right
        .get

      val response =
        client
          .reviseDoi(
            internalDoi.doi,
            com.pennsieve.doi.client.definitions.ReviseDoiRequest(
              title = "Updated Title",
              creators = IndexedSeq(
                CreatorDTO("Salvatore", "Bonno", Some("P")),
                CreatorDTO("Cherilyn", "Sarkisian")
              ),
              version = Some(1),
              description = Some("This is a description"),
              licenses = Some(
                IndexedSeq(
                  LicenseDTO(
                    "Apache 2.0",
                    "https://spdx.org/licenses/Apache-2.0.json"
                  )
                )
              ),
              owner = Some(CreatorDTO("Cherilyn", "Sarkisian")),
              externalPublications = Some(
                IndexedSeq(
                  ExternalPublicationDTO(
                    "10.1117/12.911373",
                    Some("References")
                  )
                )
              )
            ),
            authToken
          )
          .awaitFinite()
          .right
          .get

      response shouldBe ReviseDoiResponse.OK(
        DoiDTO
          .apply(
            internalDoi,
            DataciteDoi(
              TestUtilities.testDoiStr,
              List(
                Creator("Salvatore", "Bonno", Some("P"), None),
                Creator("Cherilyn", "Sarkisian", None, None)
              ),
              "Updated Title",
              TestUtilities.testDoiPublicationYear,
              version = Some(1),
              descriptions = Some(List(Description("This is a description"))),
              rightsList = List(
                Rights(
                  "Apache 2.0",
                  Some("https://spdx.org/licenses/Apache-2.0.json")
                )
              ),
              relatedIdentifiers = List(
                RelatedIdentifier(
                  relatedIdentifier = "10.1117/12.911373",
                  relationType = RelationType.References
                )
              ),
              url = None,
              state = DoiState.Findable,
              event = None,
              mode = "edit",
              owner = Some(Contributor("Cherilyn", "Sarkisian", None))
            )
          )
          .asJson
      )
    }

    "return Forbidden when not authorized to access the given dataset" in {
      val organizationId = 2
      val datasetId = 5

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val differentDatasetId = datasetId + 1

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      val internalDoi =
        TestUtilities.createDoi(ports.db)(organizationId, differentDatasetId)

      val response: ReviseDoiResponse =
        client
          .reviseDoi(
            internalDoi.doi,
            com.pennsieve.doi.client.definitions.ReviseDoiRequest(
              title = "Updated Title",
              creators = IndexedSeq(CreatorDTO("Jon", "Adams", Some("Q"))),
              version = Some(1),
              description = Some("This is a description")
            ),
            authToken
          )
          .awaitFinite()
          .right
          .get

      response shouldBe an[ReviseDoiResponse.Forbidden]

    }
  }

  "PUT /doi/:id/hide" should {
    "mark a previously Findable DOI as Registered" in {
      val organizationId = 2
      val datasetId = 5

      val internalDoi =
        TestUtilities.createDoi(ports.db)(organizationId, datasetId)

      val token: Jwt.Token =
        generateServiceToken(
          ports.jwt,
          organizationId = organizationId,
          datasetId = datasetId
        )

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      val body = com.pennsieve.doi.client.definitions.PublishDoiRequest(
        title = "this is a test",
        creators = IndexedSeq(CreatorDTO("Jon", "Adams", Some("Q"))),
        publicationYear = 2019,
        url = "https://www.url.com",
        version = Some(1),
        description = Some("This is a description"),
        licenses = Some(
          IndexedSeq(
            LicenseDTO(
              "Apache 2.0",
              "https://spdx.org/licenses/Apache-2.0.json"
            )
          )
        ),
        owner = Some(CreatorDTO("Jon", "Adams", Some("Q")))
      )

      client
        .publishDoi(internalDoi.doi, body, authToken)
        .awaitFinite()

      val response =
        client.hideDoi(internalDoi.doi, authToken).awaitFinite().right.get

      val expectedDataciteDoi = DataciteDoi(
        TestUtilities.testDoiStr,
        TestUtilities.testDoiCreators,
        TestUtilities.testDoiTitle,
        TestUtilities.testDoiPublicationYear,
        version = Some(1),
        descriptions = Some(List(Description("This is a description"))),
        owner = Some(Contributor("Jon", "Adams", Some("Q"), None)),
        rightsList = List(
          Rights(
            "Apache 2.0",
            Some("https://spdx.org/licenses/Apache-2.0.json")
          )
        ),
        relatedIdentifiers = List.empty,
        None,
        state = DoiState.Registered,
        event = Some(DoiEvent.Hide),
        mode = "edit"
      )

      val expectedResponse: DoiDTO =
        DoiDTO.apply(internalDoi, expectedDataciteDoi)

      response shouldBe HideDoiResponse.OK(expectedResponse.asJson)
    }
  }

  "GET /citations" should {

    val FoundDoi = "10.1073/pnas.74.12.5463"
    val MissingDoi = "10.1073/MISSING-DOI"

    "return and cache citations" in {
      val token: Jwt.Token =
        generateServiceToken(ports.jwt, organizationId = 2, datasetId = 5)

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      val citation = client
        .getCitations(List(FoundDoi, MissingDoi), authToken)
        .awaitFinite()
        .right
        .get
        .asInstanceOf[GetCitationsResponse.MultiStatus]
        .value

      citation should contain theSameElementsAs List(
        CitationDTO(
          status = 200,
          doi = FoundDoi,
          citation = Some("A citation")
        ),
        CitationDTO(status = 404, doi = MissingDoi, citation = None)
      )

      ports.db
        .run(CitationCacheMapper.get(FoundDoi))
        .awaitFinite()
        .map(c => (c.doi, c.citation)) shouldBe Some(
        (FoundDoi, Some("A citation"))
      )
    }

    "refresh old cached citations" in {
      val token: Jwt.Token =
        generateServiceToken(ports.jwt, organizationId = 2, datasetId = 5)

      val authToken = List(Authorization(OAuth2BearerToken(token.value)))

      val citation = client
        .getCitations(List(FoundDoi, MissingDoi), authToken)
        .awaitFinite()
        .right
        .get shouldBe an[GetCitationsResponse.MultiStatus]

      // Move the `createdAt` timestamp back in time

      ports.db
        .run(
          CitationCacheMapper
            .filter(_.doi === FoundDoi)
            .map(_.cachedAt)
            .update(OffsetDateTime.now.minusYears(1))
        )
        .awaitFinite() shouldBe 1

      // Re-requesting the citation should reset the cache timestamp

      client
        .getCitations(List(FoundDoi, MissingDoi), authToken)
        .awaitFinite()
        .right
        .get shouldBe an[GetCitationsResponse.MultiStatus]

      val cachedAt = ports.db
        .run(CitationCacheMapper.get(FoundDoi))
        .awaitFinite()
        .map(_.cachedAt)
        .get

      cachedAt should be <= OffsetDateTime.now
      cachedAt should be > OffsetDateTime.now.minusMinutes(1)
    }
  }

  "This real life DOIs string" should {

    val created: OffsetDateTime =
      OffsetDateTime.parse("2020-04-02T18:29:16.000Z")
    val updated: OffsetDateTime = OffsetDateTime.now(ZoneId.of("UTC"))

    "deserialize to a proper DOI object" in {

      val doiString =
        s"""{
    "data": {
        "id": "10.26275/a3le-xcdv",
        "type": "dois",
        "attributes": {
            "doi": "10.26275/a3le-xcdv",
            "prefix": "10.26275",
            "suffix": "a3le-xcdv",
            "identifiers": [],
            "alternateIdentifiers": [],
            "creators": [
                {
                    "nameType": "Personal",
                    "givenName": "Nicole A",
                    "familyName": "Pelot",
                    "affiliation": []
                },
                {
                    "nameType": "Personal",
                    "givenName": "J Ashley ",
                    "familyName": "Ezzell",
                    "affiliation": []
                },
                {
                    "nameType": "Personal",
                    "givenName": "Gabriel B",
                    "familyName": "Goldhagen",
                    "affiliation": []
                },
                {
                    "nameType": "Personal",
                    "givenName": "Jake E",
                    "familyName": "Cariello",
                    "affiliation": []
                },
                {
                    "nameType": "Personal",
                    "givenName": "Kara A",
                    "familyName": "Clissold",
                    "affiliation": []
                },
                {
                    "nameType": "Personal",
                    "givenName": "Warren M",
                    "familyName": "Grill",
                    "affiliation": []
                }
            ],
            "titles": [
                {
                    "title": "Quantified Morphology of the Human Vagus Nerve with Anti-Claudin-1"
                }
            ],
            "publisher": "Pennsieve Discover",
            "container": {},
            "publicationYear": 2020,
            "subjects": [],
            "contributors": [
                {
                    "nameType": "Personal",
                    "givenName": "Nicole A",
                    "familyName": "Pelot",
                    "nameIdentifiers": [
                        {
                            "schemeUri": "https://orcid.org",
                            "nameIdentifier": "https://orcid.org/0000-0003-2844-0190",
                            "nameIdentifierScheme": "ORCID"
                        }
                    ],
                    "affiliation": []
                }
            ],
            "dates": [
                {
                    "date": "2020",
                    "dateType": "Issued"
                }
            ],
            "language": null,
            "types": {
                "resourceTypeGeneral": "Dataset"
            },
            "relatedIdentifiers": [],
            "sizes": [],
            "formats": [],
            "version": "1",
            "rightsList": [
                {
                    "rights": "Creative Commons Attribution"
                }
            ],
            "descriptions": [
                {
                    "description": "This dataset provides histological cross sections of human vagus nerves that underwent immunohistochemistry (IHC) to label claudin-1 proteins.",
                    "descriptionType": "Abstract"
                },
                {
                    "description": "Collection title 1",
                    "descriptionType": "SeriesInformation"
                }
            ],
            "geoLocations": [],
            "fundingReferences": [],
            "xml": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHJlc291cmNlIHhtbG5zOnhzaT0iaHR0cDovL3d3dy53My5vcmcvMjAwMS9YTUxTY2hlbWEtaW5zdGFuY2UiIHhtbG5zPSJodHRwOi8vZGF0YWNpdGUub3JnL3NjaGVtYS9rZXJuZWwtNCIgeHNpOnNjaGVtYUxvY2F0aW9uPSJodHRwOi8vZGF0YWNpdGUub3JnL3NjaGVtYS9rZXJuZWwtNCBodHRwOi8vc2NoZW1hLmRhdGFjaXRlLm9yZy9tZXRhL2tlcm5lbC00L21ldGFkYXRhLnhzZCI+CiAgPGlkZW50aWZpZXIgaWRlbnRpZmllclR5cGU9IkRPSSI+MTAuMjYyNzUvQTNMRS1YQ0RWPC9pZGVudGlmaWVyPgogIDxjcmVhdG9ycz4KICAgIDxjcmVhdG9yPgogICAgICA8Y3JlYXRvck5hbWUgbmFtZVR5cGU9IlBlcnNvbmFsIj5BLiBQZWxvdCwgTmljb2xlPC9jcmVhdG9yTmFtZT4KICAgICAgPGdpdmVuTmFtZT5OaWNvbGU8L2dpdmVuTmFtZT4KICAgICAgPGZhbWlseU5hbWU+QS4gUGVsb3Q8L2ZhbWlseU5hbWU+CiAgICA8L2NyZWF0b3I+CiAgICA8Y3JlYXRvcj4KICAgICAgPGNyZWF0b3JOYW1lIG5hbWVUeXBlPSJQZXJzb25hbCI+QXNobGV5IEV6emVsbCwgSi48L2NyZWF0b3JOYW1lPgogICAgICA8Z2l2ZW5OYW1lPkouPC9naXZlbk5hbWU+CiAgICAgIDxmYW1pbHlOYW1lPkFzaGxleSBFenplbGw8L2ZhbWlseU5hbWU+CiAgICA8L2NyZWF0b3I+CiAgICA8Y3JlYXRvcj4KICAgICAgPGNyZWF0b3JOYW1lIG5hbWVUeXBlPSJQZXJzb25hbCI+Qi4gR29sZGhhZ2VuLCBHYWJyaWVsPC9jcmVhdG9yTmFtZT4KICAgICAgPGdpdmVuTmFtZT5HYWJyaWVsPC9naXZlbk5hbWU+CiAgICAgIDxmYW1pbHlOYW1lPkIuIEdvbGRoYWdlbjwvZmFtaWx5TmFtZT4KICAgIDwvY3JlYXRvcj4KICAgIDxjcmVhdG9yPgogICAgICA8Y3JlYXRvck5hbWUgbmFtZVR5cGU9IlBlcnNvbmFsIj5FLiBDYXJpZWxsbywgSmFrZTwvY3JlYXRvck5hbWU+CiAgICAgIDxnaXZlbk5hbWU+SmFrZTwvZ2l2ZW5OYW1lPgogICAgICA8ZmFtaWx5TmFtZT5FLiBDYXJpZWxsbzwvZmFtaWx5TmFtZT4KICAgIDwvY3JlYXRvcj4KICAgIDxjcmVhdG9yPgogICAgICA8Y3JlYXRvck5hbWUgbmFtZVR5cGU9IlBlcnNvbmFsIj5BLiBDbGlzc29sZCwgS2FyYTwvY3JlYXRvck5hbWU+CiAgICAgIDxnaXZlbk5hbWU+S2FyYTwvZ2l2ZW5OYW1lPgogICAgICA8ZmFtaWx5TmFtZT5BLiBDbGlzc29sZDwvZmFtaWx5TmFtZT4KICAgIDwvY3JlYXRvcj4KICAgIDxjcmVhdG9yPgogICAgICA8Y3JlYXRvck5hbWUgbmFtZVR5cGU9IlBlcnNvbmFsIj5NLiBHcmlsbCwgV2FycmVuPC9jcmVhdG9yTmFtZT4KICAgICAgPGdpdmVuTmFtZT5XYXJyZW48L2dpdmVuTmFtZT4KICAgICAgPGZhbWlseU5hbWU+TS4gR3JpbGw8L2ZhbWlseU5hbWU+CiAgICA8L2NyZWF0b3I+CiAgPC9jcmVhdG9ycz4KICA8dGl0bGVzPgogICAgPHRpdGxlPlF1YW50aWZpZWQgTW9ycGhvbG9neSBvZiB0aGUgSHVtYW4gVmFndXMgTmVydmUgd2l0aCBBbnRpLUNsYXVkaW4tMSA8L3RpdGxlPgogIDwvdGl0bGVzPgogIDxwdWJsaXNoZXI+QmxhY2tmeW5uIERpc2NvdmVyPC9wdWJsaXNoZXI+CiAgPHB1YmxpY2F0aW9uWWVhcj4yMDIwPC9wdWJsaWNhdGlvblllYXI+CiAgPHJlc291cmNlVHlwZSByZXNvdXJjZVR5cGVHZW5lcmFsPSJEYXRhc2V0Ii8+CiAgPGNvbnRyaWJ1dG9ycz4KICAgIDxjb250cmlidXRvciBjb250cmlidXRvclR5cGU9Ik90aGVyIj4KICAgICAgPGNvbnRyaWJ1dG9yTmFtZSBuYW1lVHlwZT0iUGVyc29uYWwiPlBlbG90LCBOaWtraTwvY29udHJpYnV0b3JOYW1lPgogICAgICA8Z2l2ZW5OYW1lPk5pa2tpPC9naXZlbk5hbWU+CiAgICAgIDxmYW1pbHlOYW1lPlBlbG90PC9mYW1pbHlOYW1lPgogICAgICA8bmFtZUlkZW50aWZpZXIgbmFtZUlkZW50aWZpZXJTY2hlbWU9Ik9SQ0lEIiBzY2hlbWVVUkk9Imh0dHBzOi8vb3JjaWQub3JnIj5odHRwczovL29yY2lkLm9yZy8wMDAwLTAwMDMtMjg0NC0wMTkwPC9uYW1lSWRlbnRpZmllcj4KICAgIDwvY29udHJpYnV0b3I+CiAgPC9jb250cmlidXRvcnM+CiAgPGRhdGVzPgogICAgPGRhdGUgZGF0ZVR5cGU9Iklzc3VlZCI+MjAyMDwvZGF0ZT4KICA8L2RhdGVzPgogIDxzaXplcy8+CiAgPGZvcm1hdHMvPgogIDx2ZXJzaW9uPjE8L3ZlcnNpb24+CiAgPHJpZ2h0c0xpc3Q+CiAgICA8cmlnaHRzPkNyZWF0aXZlIENvbW1vbnMgQXR0cmlidXRpb248L3JpZ2h0cz4KICA8L3JpZ2h0c0xpc3Q+CiAgPGRlc2NyaXB0aW9ucz4KICAgIDxkZXNjcmlwdGlvbiBkZXNjcmlwdGlvblR5cGU9IkFic3RyYWN0Ij5UaGlzIGRhdGFzZXQgcHJvdmlkZXMgaGlzdG9sb2dpY2FsIGNyb3NzIHNlY3Rpb25zIG9mIGh1bWFuIHZhZ3VzIG5lcnZlcyB0aGF0IHVuZGVyd2VudCBpbW11bm9oaXN0b2NoZW1pc3RyeSAoSUhDKSB0byBsYWJlbCBjbGF1ZGluLTEgcHJvdGVpbnMuPC9kZXNjcmlwdGlvbj4KICA8L2Rlc2NyaXB0aW9ucz4KPC9yZXNvdXJjZT4K",
            "url": "https://discover.blackfynn.com/datasets/65/version/1",
            "contentUrl": null,
            "metadataVersion": 11,
            "schemaVersion": "http://datacite.org/schema/kernel-4",
            "source": "fabricaForm",
            "isActive": true,
            "state": "findable",
            "reason": null,
            "viewCount": 0,
            "viewsOverTime": [],
            "downloadCount": 0,
            "downloadsOverTime": [],
            "referenceCount": 0,
            "citationCount": 0,
            "citationsOverTime": [],
            "partCount": 0,
            "partOfCount": 0,
            "versionCount": 0,
            "versionOfCount": 0,
            "created": "${created.toString()}",
            "registered": "2020-04-02T18:40:15.000Z",
            "published": "2020",
            "updated": "${updated.toString()}"
        },
        "relationships": {
            "client": {
                "data": {
                    "id": "bf.discover",
                    "type": "clients"
                }
            },
            "media": {
                "data": {
                    "id": "10.26275/a3le-xcdv",
                    "type": "media"
                }
            },
            "references": {
                "data": []
            },
            "citations": {
                "data": []
            },
            "parts": {
                "data": []
            },
            "partOf": {
                "data": []
            },
            "versions": {
                "data": []
            },
            "versionOf": {
                "data": []
            }
        }
    }
}"""

      val expectedDoi = DataciteDoi(
        DoiData(
          "dois",
          DoiAttributes(
            doi = "10.26275/a3le-xcdv",
            creators = List(
              Creator(
                Some("Nicole A"),
                Some("Pelot"),
                None,
                None,
                Some("Personal")
              ),
              Creator(
                Some("J Ashley "),
                Some("Ezzell"),
                None,
                None,
                Some("Personal")
              ),
              Creator(
                Some("Gabriel B"),
                Some("Goldhagen"),
                None,
                None,
                Some("Personal")
              ),
              Creator(
                Some("Jake E"),
                Some("Cariello"),
                None,
                None,
                Some("Personal")
              ),
              Creator(
                Some("Kara A"),
                Some("Clissold"),
                None,
                None,
                Some("Personal")
              ),
              Creator(
                Some("Warren M"),
                Some("Grill"),
                None,
                None,
                Some("Personal")
              )
            ),
            titles = List(
              Title(
                "Quantified Morphology of the Human Vagus Nerve with Anti-Claudin-1"
              )
            ),
            publisher = "Pennsieve Discover",
            publicationYear = Some(2020),
            version = Some(1),
            types = Type("Dataset"),
            dates = Some(List(DoiDate("2020", "Issued"))),
            descriptions = Some(
              List(
                Description(
                  "This dataset provides histological cross sections of human vagus nerves that underwent immunohistochemistry (IHC) to label claudin-1 proteins.",
                  "Abstract"
                ),
                Description("Collection title 1", "SeriesInformation")
              )
            ),
            contributors = Some(
              List(
                Contributor(
                  "Nicole A",
                  "Pelot",
                  None,
                  List(
                    NameIdentifier(
                      "https://orcid.org/0000-0003-2844-0190",
                      "https://orcid.org",
                      "ORCID"
                    )
                  ),
                  Some("Personal"),
                  None
                )
              )
            ),
            rightsList =
              Some(List(Rights("Creative Commons Attribution", None))),
            relatedIdentifiers = Some(List.empty),
            url = Some("https://discover.blackfynn.com/datasets/65/version/1"),
            state = Some(DoiState.Findable),
            event = None,
            mode = None,
            created = Some(created.toString()),
            updated = Some(updated.toString())
          )
        )
      )

      decode[DataciteDoi](doiString) shouldBe Right(expectedDoi)

    }
  }

}
