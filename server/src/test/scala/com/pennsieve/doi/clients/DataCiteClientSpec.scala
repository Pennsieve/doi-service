// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi.clients

import com.pennsieve.doi.server.definitions.CreatorDto
import com.pennsieve.doi.models.{
  Creator,
  DataciteDoi,
  NameIdentifier,
  RelatedIdentifier,
  RelationType
}
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DataCiteClientSpec extends AnyWordSpec with Matchers {

  val paper =
    RelatedIdentifier(
      "10.1117/12.911373",
      relationType = RelationType.IsSourceOf
    )
  val previous =
    RelatedIdentifier(
      "10.26275/bk6f-qskp",
      relationType = RelationType.IsNewVersionOf
    )
  val next =
    RelatedIdentifier(
      "10.26275/abcd-efgh",
      relationType = RelationType.IsPreviousVersionOf
    )

  "merge" should {
    "add only what's missing, keeping what's there" in {
      DataCiteClient.merge(List(paper, previous), List(previous, next)) shouldBe
        List(paper, previous, next)
    }

    "compare DOIs without case" in {
      DataCiteClient.merge(
        List(previous),
        List(previous.copy(relatedIdentifier = "10.26275/BK6F-QSKP"))
      ) shouldBe List(previous)
    }

    "keep the same DOI under another relation" in {
      DataCiteClient.merge(
        List(paper),
        List(paper.copy(relationType = RelationType.References))
      ) should have length 2
    }
  }

  "versionLinks" should {
    "keep only the relations between versions" in {
      DataCiteClient.versionLinks(List(paper, previous, next)) shouldBe List(
        previous,
        next
      )
    }
  }

  "RelatedIdentifier" should {
    "decode every DataCite relation type, as DataCite returns them" in {
      for (relation <- List(
          "IsNewVersionOf",
          "IsPreviousVersionOf",
          "HasPart",
          "IsPartOf",
          "Cites",
          "Other"
        )) {
        decode[RelatedIdentifier](
          s"""{"relatedIdentifier":"10.26275/qcmb-kmbx","relatedIdentifierType":"DOI","relationType":"$relation"}"""
        ).map(_.relationType.entryName) shouldBe Right(relation)
      }
    }
  }

  "creator" should {
    "be a person by given and family name, with ORCID" in {
      DataCiteClient.creator(
        CreatorDto("Ada", "Lovelace", None, Some("0000-0002-1825-0097"))
      ) shouldBe Creator(
        Some("Ada"),
        Some("Lovelace"),
        Some("Ada Lovelace"),
        Some(List(NameIdentifier("0000-0002-1825-0097"))),
        Some("Personal")
      )
    }

    "be an organisation by its name whole, with nameType Organizational" in {
      val org = DataCiteClient.creator(
        CreatorDto(
          "Pennsieve (University of Pennsylvania)",
          " ",
          nameType = Some("Organizational")
        )
      )
      org shouldBe Creator(
        None,
        None,
        Some("Pennsieve (University of Pennsylvania)"),
        None,
        Some("Organizational")
      )
      val json = org.asJson
      json.hcursor.get[String]("nameType") shouldBe Right("Organizational")
      json.hcursor.get[Option[String]]("givenName") shouldBe Right(None)
    }
  }

  "DataciteDoi" should {
    "be a Dataset unless another resource type is given" in {
      def doi(resourceType: Option[String]) = {
        val d = resourceType.fold(
          DataciteDoi(
            "10.0/x",
            List(Creator.organization("Lab")),
            "Title",
            Some(2026),
            Some(1),
            None,
            None,
            List.empty,
            List.empty
          )
        )(
          t =>
            DataciteDoi(
              "10.0/x",
              List(Creator.organization("Lab")),
              "Title",
              Some(2026),
              Some(1),
              None,
              None,
              List.empty,
              List.empty,
              resourceTypeGeneral = t
            )
        )
        d.data.attributes.types.resourceTypeGeneral
      }
      doi(None) shouldBe "Dataset"
      doi(Some("Software")) shouldBe "Software"
    }
  }
}
