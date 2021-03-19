// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.blackfynn.doi.models

import java.time.{ OffsetDateTime, ZoneOffset }

import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import io.circe.{ Decoder, Encoder }

import scala.concurrent.duration._

case class CitationCache(
  doi: String,
  citation: Option[String],
  cachedAt: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
) {

  def stale(ttl: Duration): Boolean =
    cachedAt
      .plusSeconds(ttl.toSeconds)
      .isBefore(OffsetDateTime.now(ZoneOffset.UTC))
}

object CitationCache {
  implicit val decoder: Decoder[CitationCache] = deriveDecoder[CitationCache]
  implicit val encoder: Encoder[CitationCache] = deriveEncoder[CitationCache]

  /*
   * This is required by slick when using a companion object on a case
   * class that defines a database table
   */
  val tupled = (this.apply _).tupled
}
