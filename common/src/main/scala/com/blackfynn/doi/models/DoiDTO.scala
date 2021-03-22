// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi.models

import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }

case class DoiDTO(
  organizationId: Int,
  datasetId: Int,
  doi: String,
  title: Option[String] = None,
  url: Option[String] = None,
  createdAt: Option[String] = None,
  publicationYear: Option[Int] = None,
  state: Option[DoiState] = None,
  creators: Option[List[Option[String]]] = None
)

object DoiDTO {
  implicit val decoder: Decoder[DoiDTO] = deriveDecoder[DoiDTO]
  implicit val encoder: Encoder[DoiDTO] = deriveEncoder[DoiDTO]

  def apply(internalDoi: Doi, dataciteDoi: DataciteDoi): DoiDTO =
    DoiDTO(
      organizationId = internalDoi.organizationId,
      datasetId = internalDoi.datasetId,
      doi = dataciteDoi.data.attributes.doi,
      title = dataciteDoi.data.attributes.titles.headOption.map(_.title),
      url = dataciteDoi.data.attributes.url,
      createdAt = dataciteDoi.data.attributes.created,
      publicationYear = dataciteDoi.data.attributes.publicationYear,
      state = dataciteDoi.data.attributes.state,
      creators = Option(dataciteDoi.data.attributes.creators.map(_.name))
        .filter(_.nonEmpty)
    ) // Convert empty list to None
}
