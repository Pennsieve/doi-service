// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.blackfynn.doi

import com.blackfynn.doi.db.DoiMapper
import com.blackfynn.doi.db.profile.api._
import com.blackfynn.doi.models.{
  Contributor,
  Creator,
  DataciteDoi,
  Description,
  Doi,
  Rights
}
import com.blackfynn.test.AwaitableImplicits

import scala.concurrent.ExecutionContext
import scala.util.Random

object TestUtilities extends AwaitableImplicits {

  val existingDoiTitle = "I think, therefore I am"
  val testDoiStr = "10.000/0000"
  val testDoiCreators =
    List(
      Creator("test", "user1", None, Some("0000-1111-2222-3333")),
      Creator("test ", "user2", Some("I"), None)
    )
  val testDoiTitle = "Test Title"
  val testDoiPublicationYear = Some(2019)
  val testVersion = Some(1)
  val testDescription = Some(List(Description("This is a description")))
  val testRights = List(
    Rights("Apache 2.0", Some("https://spdx.org/licenses/Apache-2.0.json"))
  )
  val testDoiContributors = Some(
    Contributor("dataset", "owner", Some("9999-8888-7777-6666"))
  )
  val testDoi: DataciteDoi =
    DataciteDoi(
      testDoiStr,
      testDoiCreators,
      testDoiTitle,
      testDoiPublicationYear,
      testVersion,
      testDescription,
      rightsList = testRights,
      relatedIdentifiers = List.empty,
      owner = testDoiContributors
    )

  def randomDoi(length: Int = 4): String = {
    s"10.21397/${Random.alphanumeric take length mkString}-${Random.alphanumeric take length mkString}".toLowerCase
  }

  def createDoi(
    db: Database
  )(
    organizationId: Int = 1,
    datasetId: Int = 1,
    doi: String = randomDoi(),
    version: Int = 1
  )(implicit
    executionContext: ExecutionContext
  ): Doi = {

    db.run(DoiMapper.create(organizationId, datasetId, doi)).await
  }
}
