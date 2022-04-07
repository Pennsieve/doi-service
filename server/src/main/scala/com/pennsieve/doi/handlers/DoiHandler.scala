// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi.handlers

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import cats.data._
import cats.implicits._
import com.pennsieve.auth.middleware.Jwt
import com.pennsieve.auth.middleware.AkkaDirective.authenticateJwt
import com.pennsieve.doi.Authenticator.withAuthorization
import com.pennsieve.doi.db.{ CitationCacheMapper, DoiMapper }
import com.pennsieve.doi.{
  CitationNotFound,
  DuplicateDoiException,
  ForbiddenException,
  NoDatasetDoiException,
  NoDoiException,
  Ports
}
import com.pennsieve.doi.db.profile.api._
import com.pennsieve.doi.logging.logRequestAndResponse
import com.pennsieve.doi.logging.DoiLogContext
import com.pennsieve.doi.models.{ Citation, DataciteDoi, Doi, DoiDTO }
import com.pennsieve.doi.server.definitions
import com.pennsieve.doi.server.definitions._
import com.pennsieve.doi.server.doi.{
  DoiHandler => GuardrailHandler,
  DoiResource => GuardrailResource
}

import io.circe.syntax._
import java.net.URLDecoder
import java.time.{ OffsetDateTime, ZoneId, ZoneOffset }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random
import scala.util.control.NonFatal
import scala.concurrent.duration._

class DoiHandler(
  claim: Jwt.Claim
)(implicit
  ports: Ports,
  executionContext: ExecutionContext
) extends GuardrailHandler {

  override def getDoi(
    respond: GuardrailResource.getDoiResponse.type
  )(
    doi: String
  ): Future[GuardrailResource.getDoiResponse] = {
    implicit val logContext = DoiLogContext(doi = Some(doi))

    ports.db
      .run(DoiMapper.getDoi(doi))
      .flatMap { internalDoi =>
        withAuthorization[GuardrailResource.getDoiResponse](
          claim,
          internalDoi.organizationId,
          internalDoi.datasetId
        ) {

          ports.dataCiteClient.getDoi(internalDoi.doi).map { dataciteDoi =>
            GuardrailResource.getDoiResponse
              .OK(
                DoiDTO
                  .apply(internalDoi, dataciteDoi)
                  .asJson
              )
          }

        }.recover {
          case ForbiddenException(e) =>
            GuardrailResource.getDoiResponse.Forbidden(e)
          case NonFatal(e) =>
            GuardrailResource.getDoiResponse.InternalServerError(e.toString)
        }
      }
      .recover {
        case NoDoiException(_) =>
          GuardrailResource.getDoiResponse.NotFound(
            s"DOI $doi could not be found"
          )
      }
  }

  override def getLatestDoi(
    respond: GuardrailResource.getLatestDoiResponse.type
  )(
    organizationId: Int,
    datasetId: Int
  ): Future[GuardrailResource.getLatestDoiResponse] = {
    implicit val logContext = DoiLogContext(
      organizationId = Some(organizationId),
      datasetId = Some(datasetId)
    )

    withAuthorization[GuardrailResource.getLatestDoiResponse](
      claim,
      organizationId,
      datasetId
    ) {
      val result = for {
        internalDoi <- ports.db
          .run(DoiMapper.getLatestDatasetDoi(organizationId, datasetId))
        dataciteDoi <- ports.dataCiteClient.getDoi(internalDoi.doi)

      } yield (internalDoi, dataciteDoi)

      result.map {
        case (internalDoi, dataciteDoi) =>
          GuardrailResource.getLatestDoiResponse.OK(
            DoiDTO.apply(internalDoi, dataciteDoi).asJson
          )
      }
    }.recover {
      case NoDatasetDoiException(_, _) =>
        GuardrailResource.getLatestDoiResponse.NotFound(
          s"doi for organizationId=$organizationId datasetId=$datasetId"
        )
      case ForbiddenException(e) =>
        GuardrailResource.getLatestDoiResponse.Forbidden(e)
      case NonFatal(e) =>
        GuardrailResource.getLatestDoiResponse.InternalServerError(e.toString)
    }
  }

  override def createDraftDoi(
    respond: GuardrailResource.createDraftDoiResponse.type
  )(
    organizationId: Int,
    datasetId: Int,
    body: definitions.CreateDraftDoiRequest
  ): Future[GuardrailResource.createDraftDoiResponse] = {
    withAuthorization[GuardrailResource.createDraftDoiResponse](
      claim,
      organizationId,
      datasetId
    ) {
      val suffix = body.suffix.getOrElse(randomSuffix())
      val doi = buildDoi(suffix)
      implicit val logContext = DoiLogContext(
        doi = Some(doi),
        organizationId = Some(organizationId),
        datasetId = Some(datasetId)
      )

      val query = for {
        internalDoi <- DoiMapper.create(organizationId, datasetId, doi)
        dataciteDoi <- DBIO.from(
          ports.dataCiteClient.createDoi(
            suffix,
            body.title.getOrElse(""),
            body.creators.map(_.toList).getOrElse(List.empty),
            body.publicationYear,
            version = body.version,
            description = body.description,
            licenses = body.licenses.map(_.toList),
            owner = body.owner
          )
        )
      } yield DoiDTO.apply(internalDoi, dataciteDoi)

      ports.db.run(query.transactionally).map { doi =>
        GuardrailResource.createDraftDoiResponseCreated(doi.asJson)
      }

    }.recover {
      case ForbiddenException(e) =>
        GuardrailResource.createDraftDoiResponse.Forbidden(e)
      case DuplicateDoiException =>
        GuardrailResource.createDraftDoiResponse.BadRequest(
          s"The requested doi is already in use"
        )
      case NonFatal(e) =>
        GuardrailResource.createDraftDoiResponse.InternalServerError(e.toString)
    }
  }

  override def publishDoi(
    respond: GuardrailResource.publishDoiResponse.type
  )(
    doi: String,
    body: PublishDoiRequest
  ): Future[GuardrailResource.publishDoiResponse] = {
    val decodedDoi = URLDecoder.decode(doi, "utf-8")
    implicit val logContext = DoiLogContext(doi = Some(decodedDoi))
    ports.log.info(s"Requested DOI: $doi | Decoded DOI: $decodedDoi")
    ports.db
      .run(DoiMapper.getDoi(decodedDoi))
      .flatMap { internalDoi =>
        {

          withAuthorization[GuardrailResource.publishDoiResponse](
            claim,
            internalDoi.organizationId,
            internalDoi.datasetId
          ) {
            ports.log.info("Publishing DOI")
            ports.dataCiteClient
              .publishDoi(
                doi = internalDoi.doi,
                title = body.title,
                creators = body.creators.toList,
                publicationYear = body.publicationYear,
                url = body.url,
                publisher = body.publisher,
                version = body.version,
                description = body.description,
                licenses = body.licenses.map(_.toList),
                owner = body.owner,
                collections = body.collections.map(_.toList),
                externalPublications = body.externalPublications.map(_.toList)
              )
              .map { publishedDoi =>
                ports.log.info(
                  s"Datacite Publish Response: ${publishedDoi.asJson.noSpaces}"
                )
                GuardrailResource.publishDoiResponse.OK(
                  DoiDTO.apply(internalDoi, publishedDoi).asJson
                )
              }
          }
        }
      }
      .recover {
        case NoDoiException(_) =>
          ports.log.error(
            s"Requested DOI: $doi | Decoded DOI: $decodedDoi could not be found"
          )
          GuardrailResource.publishDoiResponse.NotFound(
            s"DOI $doi could not be found"
          )
        case ForbiddenException(e) =>
          GuardrailResource.publishDoiResponse.Forbidden(e)
        case NonFatal(e) => {
          ports.log.error(e.toString)
          GuardrailResource.publishDoiResponse.InternalServerError(e.toString)
        }
      }
  }

  override def hideDoi(
    respond: GuardrailResource.hideDoiResponse.type
  )(
    doi: String
  ): Future[GuardrailResource.hideDoiResponse] = {
    implicit val logContext = DoiLogContext(doi = Some(doi))
    ports.db
      .run(DoiMapper.getDoi(doi))
      .flatMap { internalDoi =>
        {
          withAuthorization[GuardrailResource.hideDoiResponse](
            claim,
            internalDoi.organizationId,
            internalDoi.datasetId
          ) {
            ports.log.info("Hiding DOI")
            ports.dataCiteClient
              .hideDoi(doi)
              .map { hiddenDoi =>
                ports.log.info(
                  s"Datacite Hide Response: ${hiddenDoi.asJson.noSpaces}"
                )
                GuardrailResource.hideDoiResponse.OK(
                  DoiDTO.apply(internalDoi, hiddenDoi).asJson
                )
              }
          }
        }
      }
      .recover {
        case NoDoiException(_) =>
          GuardrailResource.hideDoiResponse.NotFound(
            s"DOI $doi could not be found"
          )
        case ForbiddenException(e) =>
          GuardrailResource.hideDoiResponse.Forbidden(e)
        case NonFatal(e) => {
          ports.log.error(e.toString)
          GuardrailResource.hideDoiResponse.InternalServerError(e.toString)
        }
      }
  }

  override def reviseDoi(
    respond: GuardrailResource.reviseDoiResponse.type
  )(
    doi: String,
    body: ReviseDoiRequest
  ): Future[GuardrailResource.reviseDoiResponse] = {
    implicit val logContext = DoiLogContext(doi = Some(doi))

    val now: OffsetDateTime = OffsetDateTime.now(ZoneId.of("UTC"))
    val query = for {
      internalDoi <- DoiMapper.getDoi(doi)
      revisedDoi <- DBIO.from {
        withAuthorization[DataciteDoi](
          claim,
          internalDoi.organizationId,
          internalDoi.datasetId
        ) {
          ports.log.info("Updating DOI")
          ports.dataCiteClient
            .reviseDoi(
              doi = doi,
              title = body.title,
              creators = body.creators.toList,
              version = body.version,
              description = body.description,
              licenses = body.licenses.map(_.toList),
              owner = body.owner,
              collections = body.collections.map(_.toList),
              externalPublications = body.externalPublications.map(_.toList),
              updated = Some(now)
            )
        }
      }
      _ <- DoiMapper.setUpdatedAt(doi, now)
    } yield (internalDoi, revisedDoi)

    ports.db
      .run(query.transactionally)
      .map {
        case (internalDoi, revisedDoi) => {
          GuardrailResource.reviseDoiResponse.OK(
            DoiDTO.apply(internalDoi, revisedDoi).asJson
          )
        }
      }
      .recover {
        case NoDoiException(_) =>
          GuardrailResource.reviseDoiResponse.NotFound(
            s"DOI $doi could not be found"
          )
        case ForbiddenException(e) =>
          GuardrailResource.reviseDoiResponse.Forbidden(e)
        case NonFatal(e) => {
          ports.log.error(e.toString)
          GuardrailResource.reviseDoiResponse.InternalServerError(e.toString)
        }
      }
  }

  /**
    * Note: roles in JWT do not matter for this endpoint, only that the JWT is
    * valid.
    */
  def getCitations(
    respond: GuardrailResource.getCitationsResponse.type
  )(
    dois: Iterable[String]
  ): Future[GuardrailResource.getCitationsResponse] = {

    // Run requests in parallel with map/sequence (instead of traverse)

    dois.toList
      .map(getAndCacheCitation(_, 1.day))
      .sequence
      .map(
        citations =>
          GuardrailResource.getCitationsResponse
            .MultiStatus(citations.toIndexedSeq)
      )
      .recover {
        case NonFatal(e) => {
          ports.logger.noContext.error(e.getMessage)
          GuardrailResource.getCitationsResponse.InternalServerError(e.toString)
        }
      }
  }

  /**
    * The Crosscite API is very slow (several seconds for a response).
    * We cache the citation data in Postgres for faster retrieval.
    */
  def getAndCacheCitation(doi: String, ttl: Duration): Future[CitationDTO] = {
    implicit val logContext = DoiLogContext(doi = Some(doi))

    for {
      cachedCitation <- ports.db.run(CitationCacheMapper.get(doi))

      citation <- cachedCitation match {
        // Use cached
        case Some(cache) if !cache.stale(ttl) =>
          ports.log.info(
            s"Using cached citation for DOI $doi: ${cache.citation} "
          )
          Future.successful(cache.citation)

        // Refresh and store
        case _ =>
          for {
            citation <- ports.citationClient
              .getCitation(doi)
              .map(_.citation.some)
              .recover {
                case CitationNotFound(e) =>
                  ports.log.error(e)
                  None
              }

            _ = ports.log.info(s"Caching citation for DOI $doi: $citation ")
            _ <- ports.db.run(CitationCacheMapper.insertOrUpdate(doi, citation))
          } yield citation
      }

    } yield
      citation match {
        case Some(citation) =>
          CitationDTO(status = 200, doi = doi, citation = Some(citation))
        case None => CitationDTO(status = 404, doi = doi, citation = None)
      }
  }

  def buildDoi(suffix: String): String = {
    s"${ports.config.dataCite.pennsievePrefix}/$suffix"
  }

  def randomSuffix(length: Int = 4): String = {
    s"${Random.alphanumeric take length mkString}-${Random.alphanumeric take length mkString}".toLowerCase
  }

}

object DoiHandler {

  def routes(
    ports: Ports
  )(implicit
    system: ActorSystem,
    executionContext: ExecutionContext
  ): Route = {
    logRequestAndResponse(ports) {
      authenticateJwt(system.name)(ports.jwt) { claim =>
        GuardrailResource.routes(new DoiHandler(claim)(ports, executionContext))
      }
    }
  }
}
