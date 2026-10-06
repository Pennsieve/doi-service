// Copyright (c) 2026 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class WorkspaceDoiConfigSpec extends AnyWordSpec with Matchers {

  val config = ConfigFactory.parseString(
    """{
      |  "default": { "fieldsOfScience": ["Basic medicine"] },
      |  "prod": {
      |    "367": { "fieldsOfScience": ["Biological sciences", "Medical biotechnology"] },
      |    "668": { "subjects": ["Neuroscience"] }
      |  }
      |}""".stripMargin
  )

  "WorkspaceDoiConfig" should {
    "give a workspace its settings for the environment" in {
      WorkspaceDoiConfig.load(Some("prod"), config).forWorkspace(367) shouldBe
        WorkspaceDoiSettings(
          fieldsOfScience = List("Biological sciences", "Medical biotechnology")
        )
    }

    "give the default's fields of science to a workspace without its own" in {
      WorkspaceDoiConfig.load(Some("prod"), config).forWorkspace(668) shouldBe
        WorkspaceDoiSettings(
          fieldsOfScience = List("Basic medicine"),
          subjects = List("Neuroscience")
        )
    }

    "give the default to other workspaces and environments" in {
      val default = WorkspaceDoiSettings(fieldsOfScience = List("Basic medicine"))
      WorkspaceDoiConfig.load(Some("prod"), config).forWorkspace(20) shouldBe default
      WorkspaceDoiConfig.load(Some("dev"), config).forWorkspace(367) shouldBe default
      WorkspaceDoiConfig.load(None, config).forWorkspace(367) shouldBe default
    }

    "read the configured workspaces" in {
      val prod = WorkspaceDoiConfig.load(Some("prod"))
      prod.forWorkspace(367).fieldsOfScience shouldBe List(
        "Basic medicine",
        "Biological sciences",
        "Medical biotechnology"
      )
      prod.forWorkspace(668).subjects shouldBe List("Neuroscience")
      WorkspaceDoiConfig.load(Some("dev")).forWorkspace(28).fieldsOfScience should
        contain("Medical biotechnology")
    }
  }
}
