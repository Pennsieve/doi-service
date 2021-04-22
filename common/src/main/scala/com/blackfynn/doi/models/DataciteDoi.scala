// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi.models

import enumeratum.EnumEntry.Lowercase
import enumeratum.{ CirceEnum, Enum, EnumEntry }
import io.circe.generic.semiauto._
import io.circe.{ Decoder, Encoder }
import enumeratum.EnumEntry.Camelcase

case class NameIdentifier(
  nameIdentifier: String,
  schemeUri: String,
  nameIdentifierScheme: String
)
object NameIdentifier {
  implicit val decoder: Decoder[NameIdentifier] = deriveDecoder
  implicit val encoder: Encoder[NameIdentifier] = deriveEncoder

  def apply(orcid: String): NameIdentifier = {
    NameIdentifier("https://orcid.org/" + orcid, "https://orcid.org", "ORCID")
  }
}

case class Creator(
  givenName: Option[String],
  familyName: Option[String],
  name: Option[String],
  nameIdentifiers: Option[List[NameIdentifier]] = None,
  nameType: Option[String] = Some("Personal")
)

object Creator {
  implicit val decoder: Decoder[Creator] = deriveDecoder
  implicit val encoder: Encoder[Creator] = deriveEncoder

  def givenName(
    firstName: String,
    middleInitial: Option[String]
  ): String = {
    middleInitial match {
      case Some(m) => firstName + " " + m
      case None => firstName
    }
  }

  def fullName(
    firstName: String,
    lastName: String,
    middleInitial: Option[String]
  ): String = {
    s"${givenName(firstName, middleInitial)} $lastName"
  }

  def apply(
    firstName: String,
    lastName: String,
    middleInitial: Option[String],
    orcid: Option[String]
  ): Creator = {
    Creator(
      Some(givenName(firstName, middleInitial)),
      Some(lastName),
      Some(fullName(firstName, lastName, middleInitial)),
      orcid.map(o => List(NameIdentifier(o)))
    )
  }
}

case class Contributor(
  givenName: String,
  familyName: String,
  name: Option[String],
  nameIdentifiers: List[NameIdentifier] = List(),
  nameType: Option[String] = Some("Personal"),
  contributorType: Option[String] = Some("ContactPerson")
)
object Contributor {
  implicit val decoder: Decoder[Contributor] = deriveDecoder
  implicit val encoder: Encoder[Contributor] = deriveEncoder

  def givenName(
    firstName: String,
    middleInitial: Option[String]
  ): String = {
    middleInitial match {
      case Some(m) => firstName + " " + m
      case None => firstName
    }
  }

  def fullName(
    firstName: String,
    lastName: String,
    middleInitial: Option[String]
  ): String = {
    s"${givenName(firstName, middleInitial)} $lastName"
  }

  def apply(
    firstName: String,
    lastName: String,
    middleInitial: Option[String],
    orcid: Option[String]
  ): Contributor = {

    orcid match {
      case Some(o) => Contributor(
        givenName(firstName, middleInitial),
        lastName,
        Some(fullName(firstName, lastName, middleInitial)),
        List(NameIdentifier(o))
      )
      case None => Contributor(givenName(firstName, middleInitial), lastName, Some(fullName(firstName, lastName, middleInitial)))
    }
  }
}

case class Description(description: String, descriptionType: String)
object Description {
  implicit val decoder: Decoder[Description] = deriveDecoder
  implicit val encoder: Encoder[Description] = deriveEncoder

  def apply(description: String): Description = {
    Description(description, "Abstract")
  }
}

case class Rights(rights: String, rightsUri: Option[String])
object Rights {
  implicit val decoder: Decoder[Rights] = deriveDecoder
  implicit val encoder: Encoder[Rights] = deriveEncoder
}

case class Title(title: String)
object Title {
  implicit val decoder: Decoder[Title] = deriveDecoder
  implicit val encoder: Encoder[Title] = deriveEncoder
}

case class Type(resourceTypeGeneral: String)
object Type {
  implicit val decoder: Decoder[Type] = deriveDecoder
  implicit val encoder: Encoder[Type] = deriveEncoder
}

case class DoiDate(date: String, dateType: String = "Issued")
object DoiDate {
  implicit val decoder: Decoder[DoiDate] = deriveDecoder
  implicit val encoder: Encoder[DoiDate] = deriveEncoder
}

/**
  * See https://support.datacite.org/docs/schema-optional-properties-v41#122-relationtype
  * for more possible values.
  */
sealed abstract class RelationType(override val entryName: String) extends EnumEntry

object RelationType extends Enum[RelationType] with CirceEnum[RelationType] {
  val values = findValues

//  case object Cites extends RelationTypeRelationType("Cites")
//  case object Continues extends RelationType("Continues")
  case object Describes extends RelationType("Describes")
  case object Documents extends RelationType("Documents")
//  case object HasMetadata extends RelationType("HasMetadata")
  case object IsCitedBy extends RelationType("IsCitedBy")
//  case object IsCompiledBy extends RelationType("IsCompiledBy")
//  case object IsContinuedBy extends RelationType("IsContinuedBy")
  case object IsDerivedFrom extends RelationType("IsDerivedFrom")
  case object IsDescribedBy extends RelationType("IsDescribedBy")
  case object IsDocumentedBy extends RelationType("IsDocumentedBy")
  case object IsMetadataFor extends RelationType("IsMetadataFor")
  case object IsReferencedBy extends RelationType("IsReferencedBy")
  case object IsRequiredBy extends RelationType("IsRequiredBy")
  case object IsSourceOf extends RelationType("IsSourceOf")
  case object IsSupplementedBy extends RelationType("IsSupplementedBy")
  case object IsSupplementTo extends RelationType("IsSupplementTo")
  case object IsOriginalFormOf extends RelationType("IsOriginalFormOf")
  case object IsVariantFormOf extends RelationType("IsVariantFormOf")
  case object References extends RelationType("References")
  case object Requires extends RelationType("Requires")
}

case class RelatedIdentifier(
  relatedIdentifier: String,
  relatedIdentifierType: String = "DOI",
  relationType: RelationType = RelationType.References
)

object RelatedIdentifier {
  implicit val encoder: Encoder[RelatedIdentifier] = deriveEncoder
  implicit val decoder: Decoder[RelatedIdentifier] = deriveDecoder
}

case class DoiAttributes(
  doi: String,
  creators: List[Creator],
  titles: List[Title],
  publisher: String,
  publicationYear: Option[Int],
  version: Option[Int],
  types: Type,
  dates: Option[List[DoiDate]],
  descriptions: Option[List[Description]],
  contributors: Option[List[Contributor]],
  rightsList: Option[List[Rights]],
  relatedIdentifiers: Option[List[RelatedIdentifier]] = None,
  url: Option[String] = None,
  state: Option[DoiState] = None,
  event: Option[DoiEvent] = None,
  mode: Option[String] = None,
  created: Option[String] = None,
  updated: Option[String] = None
)

object DoiAttributes {
  implicit val decoder: Decoder[DoiAttributes] = deriveDecoder
  implicit val encoder: Encoder[DoiAttributes] = deriveEncoder
}

case class DoiData(`type`: String, attributes: DoiAttributes)

object DoiData {
  implicit val decoder: Decoder[DoiData] = deriveDecoder
  implicit val encoder: Encoder[DoiData] = deriveEncoder
}

sealed trait DoiState extends EnumEntry with Lowercase

object DoiState extends Enum[DoiState] with CirceEnum[DoiState] {
  val values = findValues
  case object Draft extends DoiState
  case object Registered extends DoiState
  case object Findable extends DoiState

  override def withNameOption(name: String): Option[DoiState] =
    name match {
      case "Draft" | "draft" | "DRAFT" => Some(Draft)
      case "Registered" | "registered" | "REGISTERED" => Some(Registered)
      case "Findable" | "findable" | "FINDABLE" => Some(Findable)
      case _ => super.withNameOption(name)
    }

  override def withNameInsensitiveOption(name: String) =
    withNameOption(name.toLowerCase)

  override def withNameUppercaseOnlyOption(name: String) =
    withNameOption(name.toUpperCase)

  override def withNameLowercaseOnlyOption(name: String) =
    withNameOption(name.toLowerCase)

}

sealed trait DoiEvent extends EnumEntry with Lowercase

object DoiEvent extends Enum[DoiEvent] with CirceEnum[DoiEvent] {
  val values = findValues
  case object Register extends DoiEvent
  case object Publish extends DoiEvent
  case object Hide extends DoiEvent

  override def withNameOption(name: String): Option[DoiEvent] =
    name match {
      case "Register" | "register" | "REGISTER" => Some(Register)
      case "Publish" | "publish" | "PUBLISH" => Some(Publish)
      case "Hide" | "hide" | "HIDE" => Some(Hide)
      case _ => super.withNameOption(name)
    }

  override def withNameInsensitiveOption(name: String) =
    withNameOption(name.toLowerCase)

  override def withNameUppercaseOnlyOption(name: String) =
    withNameOption(name.toUpperCase)

  override def withNameLowercaseOnlyOption(name: String) =
    withNameOption(name.toLowerCase)
}

case class DataciteDoi(data: DoiData)

object DataciteDoi {
  implicit val decoder: Decoder[DataciteDoi] = deriveDecoder
  implicit val encoder: Encoder[DataciteDoi] = deriveEncoder
  val defaultDoiType = "dois"
  val defaultPublisher = "Pennsieve Discover"
  val defaultDoiResourceType = "Dataset"
  val defaultState: DoiState = DoiState.Draft
  val defaultMode = "new"

  def apply(
    doi: String,
    creators: List[Creator],
    title: String,
    publicationYear: Option[Int],
    version: Option[Int],
    descriptions: Option[List[Description]],
    owner: Option[Contributor],
    rightsList: List[Rights],
    relatedIdentifiers: List[RelatedIdentifier],
    url: Option[String] = None,
    state: DoiState = defaultState,
    event: Option[DoiEvent] = None,
    mode: String = defaultMode
  ): DataciteDoi = {
    val doiTitles = List(Title(title))
    val doiType = Type(defaultDoiResourceType)
    val doiDate = publicationYear.map(year => List(DoiDate(year.toString)))

    DataciteDoi(
      DoiData(
        defaultDoiType,
        DoiAttributes(
          doi = doi,
          creators = creators,
          titles = doiTitles,
          publisher = defaultPublisher,
          publicationYear = publicationYear,
          version = version,
          types = doiType,
          descriptions = descriptions,
          rightsList = Some(rightsList),
          contributors = owner.map(o => List(o)),
          relatedIdentifiers = Some(relatedIdentifiers),
          dates = doiDate,
          url = url,
          state = Some(state),
          event = event,
          mode = Some(mode)
        )
      )
    )
  }
}
