// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.blackfynn.doi.db

import java.time.{ OffsetDateTime, ZoneId }

import com.blackfynn.doi.{
  DuplicateDoiException,
  NoDatasetDoiException,
  NoDoiException
}
import com.blackfynn.doi.db.profile.api._
import com.blackfynn.doi.models._
import org.postgresql.util.PSQLException

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

final class DoiTable(tag: Tag)
    extends Table[Doi](tag, Some(schema), "dataset_doi") {

  def organizationId = column[Int]("organization_id")
  def datasetId = column[Int]("dataset_id")
  def doi = column[String]("doi", O.PrimaryKey)
  def createdAt = column[OffsetDateTime]("created_at")
  def updatedAt = column[OffsetDateTime]("updated_at")

  def * =
    (organizationId, datasetId, doi, createdAt, updatedAt)
      .mapTo[Doi]
}

object DoiMapper extends TableQuery(new DoiTable(_)) {

  def get(doi: String): Query[DoiTable, Doi, Seq] =
    this.filter(_.doi.toLowerCase === doi.toLowerCase) // DOIs are case insensitive

  def getDoi(
    doi: String
  )(implicit
    executionContext: ExecutionContext
  ): DBIOAction[Doi, NoStream, Effect.Read with Effect] =
    this
      .get(doi)
      .result
      .headOption
      .flatMap {
        case None => DBIO.failed(NoDoiException(doi))
        case Some(doi) => DBIO.successful(doi)
      }

  def getByDataset(
    organizationId: Int,
    datasetId: Int
  ): Query[DoiTable, Doi, Seq] = {
    this
      .filter(_.organizationId === organizationId)
      .filter(_.datasetId === datasetId)
  }

  def getLatestDatasetDoi(
    organizationId: Int,
    datasetId: Int
  )(implicit
    executionContext: ExecutionContext
  ): DBIOAction[Doi, NoStream, Effect.Read with Effect] = {
    this
      .getByDataset(organizationId, datasetId)
      .sortBy(_.createdAt.desc)
      .take(1)
      .result
      .headOption
      .flatMap {
        case None =>
          DBIO.failed(NoDatasetDoiException(organizationId, datasetId))
        case Some(doi) => DBIO.successful(doi)
      }
  }

  def create(
    organizationId: Int,
    datasetId: Int,
    doi: String
  )(implicit
    executionContext: ExecutionContext
  ): DBIOAction[Doi, NoStream, Effect.Write with Effect] = {

    val row = Doi(organizationId, datasetId, doi)
    val query = (this returning this) += row

    query.asTry.flatMap {
      case Success(result) =>
        DBIO.successful(result)
      case Failure(exception: PSQLException) =>
        DBIO.failed(DoiExceptionMapper.apply(exception))
      case Failure(exception: Throwable) =>
        DBIO.failed(exception)
    }
  }

  def setUpdatedAt(
    doi: String,
    updateAt: OffsetDateTime
  )(implicit
    executionContext: ExecutionContext
  ) =
    this
      .get(doi)
      .map(_.updatedAt)
      .update(OffsetDateTime.now(ZoneId.of("UTC")))
}

object DoiExceptionMapper {

  def apply: Throwable => Throwable = {
    case exception: PSQLException => {
      val message: String = exception.getMessage()
      if (message
          .contains("duplicate key value violates unique constraint")) {
        DuplicateDoiException
      } else {
        exception
      }
    }
    case throwable => throwable
  }

}
