// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi.clients

import java.time.OffsetDateTime

import com.pennsieve.doi.logging.DoiLogContext
import com.pennsieve.doi.models.{
  Contributor,
  Creator,
  DataciteDoi,
  DataciteError,
  Description,
  DoiEvent,
  DoiState,
  RelatedIdentifier,
  RelationType,
  Rights,
  Title
}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{
  Accept,
  Authorization,
  BasicHttpCredentials
}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.http.scaladsl.HttpExt
import com.pennsieve.doi.{ DataCiteClientConfiguration, DataciteException }
import com.pennsieve.service.utilities.ContextLogger
import com.pennsieve.doi.server.definitions._
import monocle.macros.syntax.lens._

import io.circe.Json
import io.circe.syntax._
import org.mdedetrich.akka.http.support.CirceHttpSupport._

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Client for communicating with the Datacite DOI API.
  * See https://support.datacite.org/docs/api for full documentation.
  */
trait DataCiteClient {

  def createDoi(
    doiSuffix: String,
    title: String,
    creators: List[CreatorDTO],
    publicationYear: Option[Int],
    version: Option[Int],
    description: Option[String],
    licenses: Option[List[LicenseDTO]],
    owner: Option[CreatorDTO]
  )(implicit
    logContext: DoiLogContext
  ): Future[DataciteDoi]

  def getDoi(
    doi: String
  )(implicit
    logContext: DoiLogContext
  ): Future[DataciteDoi]

  def publishDoi(
    doi: String,
    title: String,
    creators: List[CreatorDTO],
    publicationYear: Int,
    url: String,
    publisher: Option[String],
    version: Option[Int],
    description: Option[String],
    licenses: Option[List[LicenseDTO]],
    owner: Option[CreatorDTO],
    collections: Option[List[CollectionDTO]],
    externalPublications: Option[List[ExternalPublicationDTO]]
  )(implicit
    logContext: DoiLogContext
  ): Future[DataciteDoi]

  def reviseDoi(
    doi: String,
    title: String,
    creators: List[CreatorDTO],
    version: Option[Int],
    description: Option[String],
    licenses: Option[List[LicenseDTO]],
    owner: Option[CreatorDTO],
    collections: Option[List[CollectionDTO]],
    externalPublications: Option[List[ExternalPublicationDTO]],
    updated: Option[OffsetDateTime]
  )(implicit
    logContext: DoiLogContext
  ): Future[DataciteDoi]

  def hideDoi(
    doi: String
  )(implicit
    logContext: DoiLogContext
  ): Future[DataciteDoi]

}

class DataCiteClientImpl(
  httpClient: HttpExt,
  dataCiteClientConfig: DataCiteClientConfiguration
)(implicit
  executionContext: ExecutionContext,
  materializer: Materializer
) extends DataCiteClient {

  val log = new ContextLogger().context

  val credentials =
    Authorization(
      BasicHttpCredentials(
        dataCiteClientConfig.username,
        dataCiteClientConfig.password
      )
    )
  override def createDoi(
    doiSuffix: String,
    title: String,
    creators: List[CreatorDTO],
    publicationYear: Option[Int],
    version: Option[Int],
    description: Option[String],
    licenses: Option[List[LicenseDTO]],
    owner: Option[CreatorDTO]
  )(implicit
    logContext: DoiLogContext
  ): Future[DataciteDoi] = {
    val create_uri = s"${dataCiteClientConfig.apiUrl}/dois"
    val fullDoi = s"${dataCiteClientConfig.pennsievePrefix}/$doiSuffix"

    val doiRequestBody =
      DataciteDoi(
        fullDoi,
        creators.map { c =>
          Creator(c.firstName, c.lastName, c.middleInitial, c.orcid)
        },
        title,
        publicationYear,
        version,
        descriptions = description.map(d => List(Description(d))),
        owner = owner.map(
          o => Contributor(o.firstName, o.lastName, o.middleInitial, o.orcid)
        ),
        rightsList = licenses.getOrElse(List[LicenseDTO]()).map { l =>
          Rights(l.license, Some(l.licenseUri))
        },
        relatedIdentifiers = List.empty
      )

    log.info(s"Creating DOI: ${doiRequestBody.asJson.noSpaces}")

    val createDoiRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = create_uri,
      entity = HttpEntity(
        MediaTypes.`application/vnd.api+json`,
        doiRequestBody.asJson.noSpaces
      ),
      headers = List(Accept(MediaTypes.`application/json`), credentials)
    )

    httpClient
      .singleRequest(createDoiRequest)
      .flatMap {
        case HttpResponse(StatusCodes.Created, _, entity, _) =>
          Unmarshal(entity).to[DataciteDoi]
        case resp => unmarshalError(resp)
      }
  }
  override def getDoi(
    doi: String
  )(implicit
    logContext: DoiLogContext
  ): Future[DataciteDoi] = {
    val get_uri = s"${dataCiteClientConfig.apiUrl}/dois/$doi"
    val getDoiRequest = HttpRequest(
      method = HttpMethods.GET,
      uri = get_uri,
      headers = List(Accept(MediaTypes.`application/json`), credentials)
    )
    httpClient
      .singleRequest(getDoiRequest)
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          Unmarshal(entity).to[DataciteDoi]
        case error => unmarshalError(error)
      }
  }

  override def publishDoi(
    doi: String,
    title: String,
    creators: List[CreatorDTO],
    publicationYear: Int,
    url: String,
    publisher: Option[String],
    version: Option[Int],
    description: Option[String],
    licenses: Option[List[LicenseDTO]],
    owner: Option[CreatorDTO],
    collections: Option[List[CollectionDTO]],
    externalPublications: Option[List[ExternalPublicationDTO]]
  )(implicit
    logContext: DoiLogContext
  ): Future[DataciteDoi] = {
    val update_uri = s"${dataCiteClientConfig.apiUrl}/dois/$doi"

    log.info(s"update_uri: ${update_uri}")

    val seriesDescriptions: List[Description] =
      collections
        .map(l => l.map(c => Description(c.name, "SeriesInformation")))
        .getOrElse(List.empty)

    val mainDescription: List[Description] =
      description.map(c => List(Description(c))).getOrElse(List.empty)

    val descriptions: List[Description] =
      seriesDescriptions ::: mainDescription

    val publishRequestBody = DataciteDoi(
      doi,
      creators.map { c =>
        Creator(c.firstName, c.lastName, c.middleInitial, c.orcid)
      },
      title,
      Some(publicationYear),
      version,
      url = Some(url),
      publisher = publisher,
      descriptions = Some(descriptions),
      owner = owner.map(
        o => Contributor(o.firstName, o.lastName, o.middleInitial, o.orcid)
      ),
      rightsList = licenses.getOrElse(List.empty).map { l =>
        Rights(l.license, Some(l.licenseUri))
      },
      relatedIdentifiers = externalPublications
        .getOrElse(List.empty)
        .map(ep => externalPublicationToRelatedIdentifier(ep)),
      state = DoiState.Findable,
      event = Some(DoiEvent.Publish),
      mode = "edit"
    )

    log.info(s"Publishing DOI: ${publishRequestBody.asJson.noSpaces}")

    val publishDoiRequest = HttpRequest(
      method = HttpMethods.PATCH,
      uri = update_uri,
      entity = HttpEntity(
        MediaTypes.`application/vnd.api+json`,
        publishRequestBody.asJson.noSpaces
      ),
      headers = List(Accept(MediaTypes.`application/json`), credentials)
    )
    httpClient
      .singleRequest(publishDoiRequest)
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          Unmarshal(entity).to[DataciteDoi]
        case error => unmarshalError(error)
      }
  }

  /**
    * Update DOI information.
    */
  override def reviseDoi(
    doi: String,
    title: String,
    creators: List[CreatorDTO],
    version: Option[Int],
    description: Option[String],
    licenses: Option[List[LicenseDTO]],
    owner: Option[CreatorDTO],
    collections: Option[List[CollectionDTO]],
    externalPublications: Option[List[ExternalPublicationDTO]],
    updated: Option[OffsetDateTime]
  )(implicit
    logContext: DoiLogContext
  ): Future[DataciteDoi] = {
    val updateUri = s"${dataCiteClientConfig.apiUrl}/dois/$doi"
    val seriesDescriptions: List[Description] =
      collections
        .map(l => l.map(c => Description(c.name, "SeriesInformation")))
        .getOrElse(List.empty)

    val mainDescription: List[Description] =
      description.map(c => List(Description(c))).getOrElse(List.empty)

    val descriptions: List[Description] =
      seriesDescriptions ::: mainDescription

    for {
      dataciteDoi <- getDoi(doi)

      _ <- dataciteDoi.data.attributes.state match {
        case Some(DoiState.Findable) => Future.successful(())
        case _ @state =>
          Future.failed(
            new Exception(
              s"Cannot update DOI in state $state. Must be Findable"
            )
          )
      }

      body = dataciteDoi
        .lens(_.data.attributes)
        .modify(
          _.copy(
            titles = List(Title(title)),
            creators = creators.map { c =>
              Creator(c.firstName, c.lastName, c.middleInitial, c.orcid)
            },
            descriptions = Some(descriptions),
            version = version,
            event = None,
            mode = Some("edit"),
            contributors = owner
              .map(
                o =>
                  List(
                    Contributor(
                      o.firstName,
                      o.lastName,
                      o.middleInitial,
                      o.orcid
                    )
                  )
              ),
            rightsList = Some(licenses.getOrElse(List[LicenseDTO]()).map { l =>
              Rights(l.license, Some(l.licenseUri))
            }),
            relatedIdentifiers = Some(
              externalPublications
                .getOrElse(List.empty)
                .map(ep => externalPublicationToRelatedIdentifier(ep))
            ),
            updated = updated.map(_.toString())
          )
        )

      _ = log.info(s"Updating DOI: ${body.asJson.noSpaces}")

      request = HttpRequest(
        method = HttpMethods.PATCH,
        uri = updateUri,
        entity = HttpEntity(
          MediaTypes.`application/vnd.api+json`,
          body.asJson.noSpaces
        ),
        headers = List(Accept(MediaTypes.`application/json`), credentials)
      )

      updatedDoi <- httpClient
        .singleRequest(request)
        .flatMap {
          case HttpResponse(StatusCodes.OK, _, entity, _) =>
            Unmarshal(entity).to[DataciteDoi]
          case resp => unmarshalError(resp)
        }

    } yield updatedDoi
  }

  override def hideDoi(
    doi: String
  )(implicit
    logContext: DoiLogContext
  ): Future[DataciteDoi] = {
    getDoi(doi).flatMap { dataciteDoi =>
      {
        val updateUri = s"${dataCiteClientConfig.apiUrl}/dois/$doi"

        val hideRequestBody = dataciteDoi
          .lens(_.data.attributes)
          .modify(
            _.copy(
              state = Some(DoiState.Registered),
              event = Some(DoiEvent.Hide),
              mode = Some("edit")
            )
          )

        log.info(s"Hiding DOI: ${hideRequestBody.asJson.noSpaces}")

        val hideDoiRequest = HttpRequest(
          method = HttpMethods.PATCH,
          uri = updateUri,
          entity = HttpEntity(
            MediaTypes.`application/vnd.api+json`,
            hideRequestBody.asJson.noSpaces
          ),
          headers = List(Accept(MediaTypes.`application/json`), credentials)
        )
        httpClient
          .singleRequest(hideDoiRequest)
          .flatMap {
            case HttpResponse(StatusCodes.OK, _, entity, _) =>
              Unmarshal(entity).to[DataciteDoi]
            case resp => unmarshalError(resp)
          }
      }
    }
  }

  private def unmarshalError(
    response: HttpResponse
  )(implicit
    logContext: DoiLogContext
  ): Future[DataciteDoi] =
    Unmarshal(response.entity)
      .to[Json]
      .flatMap(
        body =>
          body.as[DataciteError] match {
            case Right(e) => Future.failed(DataciteException(e))
            case Left(e) =>
              log.error("Cannot unmarshal to DataciteError", e)
              log.error(body.toString)
              Future.failed(e)
          }
      )

  private def externalPublicationToRelatedIdentifier(
    externalPublication: ExternalPublicationDTO
  ): RelatedIdentifier =
    new RelatedIdentifier(
      relatedIdentifier = externalPublication.doi,
      relationType = RelationType.withName(
        externalPublication.relationshipType
          .getOrElse("References")
      )
    )
}
