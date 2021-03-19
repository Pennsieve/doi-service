// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.blackfynn.doi.db

import java.time.OffsetDateTime

import com.blackfynn.doi.db.profile.api._
import com.blackfynn.doi.models._
import org.postgresql.util.PSQLException
import slick.dbio.Effect

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

final class CitationCacheTable(tag: Tag)
    extends Table[CitationCache](tag, Some(schema), "citation_cache") {

  def doi = column[String]("doi", O.PrimaryKey)
  def citation = column[Option[String]]("citation")
  def cachedAt = column[OffsetDateTime]("cached_at")

  def * =
    (doi, citation, cachedAt)
      .mapTo[CitationCache]
}

object CitationCacheMapper extends TableQuery(new CitationCacheTable(_)) {

  def insertOrUpdate(
    doi: String,
    citation: Option[String]
  )(implicit
    ec: ExecutionContext
  ): DBIO[Unit] =
    for {
      _ <- this.filter(_.doi === doi.toLowerCase).delete
      _ <- this += CitationCache(doi = doi.toLowerCase, citation = citation)
    } yield ()

  def get(doi: String): DBIO[Option[CitationCache]] =
    this.filter(_.doi === doi.toLowerCase).result.headOption
}
