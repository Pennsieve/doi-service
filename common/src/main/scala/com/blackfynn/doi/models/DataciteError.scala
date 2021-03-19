// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.blackfynn.doi.models

import io.circe.generic.semiauto._
import io.circe.{ Decoder, Encoder }

case class DataciteError(errors: List[DataciteErrorReason])

object DataciteError {
  implicit val decoder: Decoder[DataciteError] = deriveDecoder
  implicit val encoder: Encoder[DataciteError] = deriveEncoder
}

case class DataciteErrorReason(
  status: Option[Int],
  title: String,
  source: Option[String]
)

object DataciteErrorReason {
  implicit val decoder: Decoder[DataciteErrorReason] = deriveDecoder
  implicit val encoder: Encoder[DataciteErrorReason] = deriveEncoder
}
