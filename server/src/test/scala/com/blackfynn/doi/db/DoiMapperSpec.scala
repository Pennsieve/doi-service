// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi.db

import com.pennsieve.doi.models.Doi
import com.pennsieve.doi.{ NoDoiException, ServiceSpecHarness, TestUtilities }
import com.pennsieve.test.AwaitableImplicits
import org.scalatest.{ Matchers, WordSpec }

class DoiMapperSpec
    extends WordSpec
    with ServiceSpecHarness
    with AwaitableImplicits
    with Matchers {

  "DoiMapper" should {

    "create a new DOI and retrieve it" in {
      val doi = TestUtilities.createDoi(ports.db)(doi = "abc123")

      val result: Doi = ports.db
        .run(
          DoiMapper
            .getDoi(doi.doi)
        )
        .await

      assert(result.organizationId == doi.organizationId)
      assert(result.datasetId == doi.datasetId)
      assert(result.doi == doi.doi)

      // test case insensitivity
      val result2: Doi = ports.db
        .run(
          DoiMapper
            .getDoi("ABC123")
        )
        .await

      assert(result2.organizationId == doi.organizationId)
      assert(result2.datasetId == doi.datasetId)
      assert(result2.doi == doi.doi)
    }

    "fail to retrieve a missing DOI" in {
      an[NoDoiException] should be thrownBy {
        ports.db
          .run(
            DoiMapper
              .getDoi("12345")
          )
          .await
      }
    }

    "retrieve the latest DOI for a dataset" in {
      val organizationId = 2
      val datasetId = 5

      val doi1 =
        TestUtilities.createDoi(ports.db)(
          organizationId,
          datasetId,
          doi = "def456"
        )

      val doi2 =
        TestUtilities.createDoi(ports.db)(
          organizationId,
          datasetId,
          doi = "newDoi"
        )

      val latestDoi: Doi = ports.db
        .run(
          DoiMapper
            .getLatestDatasetDoi(organizationId, datasetId)
        )
        .await

      assert(latestDoi.organizationId == doi2.organizationId)
      assert(latestDoi.datasetId == doi2.datasetId)
      assert(latestDoi.doi == doi2.doi)
    }
  }
}
