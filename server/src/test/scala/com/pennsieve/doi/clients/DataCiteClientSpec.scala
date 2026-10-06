// Copyright (c) 2026 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi.clients

import com.pennsieve.doi.models.{ RelatedIdentifier, RelationType }
import io.circe.parser.decode
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DataCiteClientSpec extends AnyWordSpec with Matchers {

  val paper =
    RelatedIdentifier("10.1117/12.911373", relationType = RelationType.IsSourceOf)
  val previous =
    RelatedIdentifier("10.26275/bk6f-qskp", relationType = RelationType.IsNewVersionOf)
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
}
