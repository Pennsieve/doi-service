// Copyright (c) 2026 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi.models

import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.OffsetDateTime

class DoiMetadataSpec extends AnyWordSpec with Matchers {

  "sizes" should {
    "read as bytes in decimal units, and files" in {
      DoiMetadata(size = Some(3216756367026L), fileCount = Some(1355)).sizes shouldBe
        Some(List("3.2 TB", "1,355 files"))
      DoiMetadata(size = Some(999L)).sizes shouldBe Some(List("999 bytes"))
      DoiMetadata(size = Some(84530L)).sizes shouldBe Some(List("84.5 kB"))
      DoiMetadata(fileCount = Some(1)).sizes shouldBe Some(List("1 file"))
    }

    "be left out when nothing is given" in {
      DoiMetadata().sizes shouldBe None
    }
  }

  "subjects" should {
    "be the keywords, trimmed and without duplicates" in {
      DoiMetadata(keywords = Some(List(" vagus nerve", "microct", "", "microct"))).subjects shouldBe
        Some(List(Subject("vagus nerve"), Subject("microct")))
    }

    "be left out when not given, and empty when cleared" in {
      DoiMetadata().subjects shouldBe None
      DoiMetadata(keywords = Some(List.empty)).subjects shouldBe Some(List.empty)
    }

    "on revision, replace keywords but keep vocabulary terms" in {
      val fos = Subject(
        "FOS: Biological sciences",
        subjectScheme = Some("Fields of Science and Technology (FOS)"),
        schemeUri = Some("http://www.oecd.org/science/inno/38235147.pdf")
      )
      val existing = Some(List(Subject("old tag"), fos))
      DoiMetadata(keywords = Some(List("vagus nerve"))).revisedSubjects(existing) shouldBe
        Some(List(fos, Subject("vagus nerve")))
      DoiMetadata().revisedSubjects(existing) shouldBe existing
    }

    "read and write every part of a subject" in {
      val json =
        """{"subject":"FOS: Biological sciences","subjectScheme":"Fields of Science and Technology (FOS)","schemeUri":"http://www.oecd.org/science/inno/38235147.pdf"}"""
      val decoded = io.circe.parser.decode[Subject](json)
      decoded.map(_.subjectScheme) shouldBe Right(
        Some("Fields of Science and Technology (FOS)")
      )
      decoded.map(_.asJson.noSpaces) shouldBe Right(json)
    }
  }

  "dates" should {
    "replace Issued, Available and Updated with the ones given, keeping others" in {
      val metadata = DoiMetadata(
        publishedAt = Some(OffsetDateTime.parse("2026-05-06T22:05:52Z")),
        availableAt = Some(OffsetDateTime.parse("2026-06-01T03:00:00-04:00")),
        revisedAt = Some(OffsetDateTime.parse("2026-08-12T14:18:23Z"))
      )
      metadata.dates(List(DoiDate("2026"), DoiDate("2025-01-01", "Created"))) shouldBe List(
        DoiDate("2025-01-01", "Created"),
        DoiDate("2026-05-06", "Issued"),
        DoiDate("2026-06-01", "Available"),
        DoiDate("2026-08-12", "Updated")
      )
    }

    "keep the existing dates when none are given" in {
      DoiMetadata().dates(List(DoiDate("2026"))) shouldBe List(DoiDate("2026"))
    }
  }

  "Rights.withSpdx" should {
    "read the SPDX identifier from an spdx.org licence URL" in {
      Rights.withSpdx(
        "Creative Commons Attribution",
        Some("https://spdx.org/licenses/CC-BY-4.0.json")
      ) shouldBe Rights(
        "Creative Commons Attribution",
        Some("https://spdx.org/licenses/CC-BY-4.0.json"),
        Some("CC-BY-4.0"),
        Some("SPDX"),
        Some("https://spdx.org/licenses/")
      )
      Rights
        .withSpdx("Apache 2.0", Some("https://spdx.org/licenses/Apache-2.0.html"))
        .rightsIdentifier shouldBe Some("Apache-2.0")
    }

    "leave other licences without one" in {
      Rights.withSpdx("Custom", Some("https://example.org/licence")) shouldBe
        Rights("Custom", Some("https://example.org/licence"))
      Rights.withSpdx("Custom", None) shouldBe Rights("Custom", None)
    }

    "encode without empty fields" in {
      Rights("Custom", None).asJson.noSpaces shouldBe """{"rights":"Custom"}"""
    }
  }
}
