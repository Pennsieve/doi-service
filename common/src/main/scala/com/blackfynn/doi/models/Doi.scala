// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.blackfynn.doi.models

import java.time.{ OffsetDateTime, ZoneOffset }

import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import io.circe.{ Decoder, Encoder }

case class Doi(
  organizationId: Int,
  datasetId: Int,
  doi: String,
  createdAt: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
  updatedAt: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
)

object Doi {
  implicit val decoder: Decoder[Doi] = deriveDecoder[Doi]
  implicit val encoder: Encoder[Doi] = deriveEncoder[Doi]

  /*
   * This is required by slick when using a companion object on a case
   * class that defines a database table
   */
  val tupled = (this.apply _).tupled
}
