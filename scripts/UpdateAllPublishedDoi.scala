import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import cats.data.EitherT
import cats.implicits._
import com.pennsieve.discover.client.dataset.DatasetClient._
import com.pennsieve.discover.client.definitions._
import com.pennsieve.discover.client.dataset.DatasetClient
import com.pennsieve.discover.client.search.SearchClient
import com.pennsieve.doi.clients.{ DataCiteClient, DataCiteClientImpl }
import com.pennsieve.doi.{ DataCiteClientConfiguration, DataciteException }
import com.pennsieve.doi.server.definitions.{
  CollectionDto,
  CreatorDto,
  ExternalPublicationDto,
  LicenseDto
}
import com.pennsieve.doi.logging.DoiLogContext
import com.pennsieve.doi.models.{
  Contributor,
  Creator,
  DataciteDoi,
  DataciteError,
  Description,
  DoiEvent,
  DoiState,
  Rights,
  Title
}
import com.pennsieve.models.License._
import com.pennsieve.models.License
import com.pennsieve.service.utilities.ContextLogger
import com.pennsieve.service.utilities.SingleHttpResponder
import monocle.macros.syntax.lens._
import io.circe.Json
import io.circe.syntax._

import java.time.{ OffsetDateTime, ZoneId }
import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

sealed trait CoreError extends Exception

case class DiscoverError(message: String) extends CoreError {
  final override def getMessage: String = message
}

case class DiscoverIgnorableError(message: String) extends CoreError {
  final override def getMessage: String = message
}

object UpdateAllPublishedDoi extends App {

  implicit val system: ActorSystem = ActorSystem("doi-service-script")
  implicit val executionContext: ExecutionContext = system.dispatcher

  private def handleGuardrailError(
  ): Either[Throwable, HttpResponse] => Future[CoreError] =
    _.fold(
      error => Future.successful(DiscoverError(error.getMessage)),
      resp =>
        resp.entity.toStrict(1.second).map { entity =>
          DiscoverError(s"HTTP ${resp.status}: ${entity.data.utf8String}")
        }
    )

  private def toCreatorDto(
    firstName: String,
    middleInitial: Option[String],
    lastName: String,
    orcid: Option[String]
  ): CreatorDto = {
    CreatorDto(firstName, lastName, middleInitial, orcid)
  }

  private def toCollectionDto(
    discoverCollection: PublicCollectionDto
  ): CollectionDto = {
    CollectionDto(discoverCollection.name, discoverCollection.id)
  }

  private def toExternalPublicationDto(
    discoverExternalPub: PublicExternalPublicationDto
  ): ExternalPublicationDto = {
    ExternalPublicationDto(
      discoverExternalPub.doi,
      Some(discoverExternalPub.relationshipType.entryName)
    )
  }

  if (args.length != 6) {
    println("usage:")
    println(
      "UpdateAllPublishedDoi dryRun discoverHost dataciteUsername datacitePassword dataciteApiUrl datacitePrefix"
    )
  } else {
    val dryRun = Try(args(0).toBoolean).getOrElse(true)
    val discoverHost = args(1)
    val dataciteUsername = args(2)
    val datacitePassword = args(3)
    val dataciteApiUrl = args(4)
    val datacitePrefix = args(5)

    lazy val datasetClient: DatasetClient = {
      val client = new SingleHttpResponder().responder
      DatasetClient.httpClient(client, discoverHost)
    }

    val dataCiteClient: DataCiteClient =
      new DataCiteClientImpl(
        Http(),
        DataCiteClientConfiguration(
          username = dataciteUsername,
          password = datacitePassword,
          apiUrl = dataciteApiUrl,
          pennsievePrefix = datacitePrefix
        )
      )

    val res = for {
      datasets <- datasetClient
        .getDatasets(limit = Some(1000))
        .leftSemiflatMap(handleGuardrailError())
        .flatMap {
          _.fold[EitherT[Future, CoreError, DatasetsPage]](
            handleOK = response => EitherT.pure(response),
            handleBadRequest = msg => EitherT.leftT(DiscoverError(msg)),
            handleNotFound = msg => EitherT.leftT(DiscoverError(msg))
          )
        }

    } yield datasets

    val datasetPage = Await.result(res.value, 3.minutes).right.get

    datasetPage.datasets.map { dataset: PublicDatasetDto =>
      {
        var a = 0
        for (a <- 1 to dataset.version) {

          val datasetVersion = Await.result(
            datasetClient
              .getDatasetVersion(dataset.id, a)
              .leftSemiflatMap(handleGuardrailError())
              .flatMap {
                _.fold[EitherT[Future, CoreError, PublicDatasetDto]](
                  handleOK = response => EitherT.pure(response),
                  handleGone =
                    msg => EitherT.leftT(DiscoverIgnorableError("Unpublished")),
                  handleNotFound = msg => EitherT.leftT(DiscoverError(msg))
                )
              }
              .value,
            3.minutes
          )

          datasetVersion match {
            case Left(e: DiscoverIgnorableError) => {
              println(
                s"dataset #${dataset.id}, version #${a} not available\n\tReason: ${e.getMessage}"
              )
            }
            case Left(e) => {
              throw new DiscoverError(
                s"dataset #${dataset.id}, version #${a} not available\n\tReason: ${e.getMessage}"
              )
            }
            case Right(dsV) => {

              val owner = toCreatorDto(
                dsV.ownerFirstName,
                None,
                dsV.ownerLastName,
                Some(dsV.ownerOrcid)
              )
              val creators = dsV.contributors.map { c =>
                toCreatorDto(c.firstName, c.middleInitial, c.lastName, c.orcid)
              }

              implicit val logContext = DoiLogContext(doi = Some(dsV.doi))

              if (dryRun) {
                val dataciteDoi =
                  Await.result(dataCiteClient.getDoi(dsV.doi), 3.minutes)

                val requestBody = dataciteDoi
                  .lens(_.data.attributes)
                  .modify(
                    _.copy(
                      titles = List(Title(dsV.name)),
                      creators = creators.map { c =>
                        Creator(
                          firstName = c.firstName,
                          lastName = c.lastName,
                          middleInitial = None,
                          orcid = c.orcid
                        )
                      }.toList,
                      descriptions = Some(List(Description(dsV.description))),
                      version = Some(dsV.version),
                      publisher = DataciteDoi.defaultPublisher,
                      event = None,
                      mode = Some("edit"),
                      contributors = Some(
                        List(
                          Contributor(
                            owner.firstName,
                            owner.lastName,
                            owner.orcid
                          )
                        )
                      ),
                      rightsList = Some(
                        List(
                          Rights(
                            dsV.license.entryName,
                            License.licenseUri.get(dsV.license)
                          )
                        )
                      )
                    )
                  )

                println(
                  s"mocking call to update the doi for dataset #${dataset.id}, version#${a}"
                )
                println(s"requestBody: ${requestBody}")

              } else {
                val now: OffsetDateTime = OffsetDateTime.now(ZoneId.of("UTC"))
                Await.result(
                  dataCiteClient.reviseDoi(
                    doi = dsV.doi,
                    title = dsV.name,
                    creators = creators.toList,
                    version = Some(dsV.version),
                    description = Some(dsV.description),
                    licenses = Some(
                      List(
                        LicenseDto(
                          dsV.license.entryName,
                          License.licenseUri.get(dsV.license).getOrElse("")
                        )
                      )
                    ),
                    owner = Some(owner),
                    collections =
                      dsV.collections.map(_.toList.map(toCollectionDto)),
                    externalPublications = dsV.externalPublications
                      .map(_.toList.map(toExternalPublicationDto)),
                    Some(now)
                  ),
                  3.minutes
                )
              }
            }
          }
        }
      }
    }
    println("Done")
  }
}
