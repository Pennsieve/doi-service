// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi.db

import com.pennsieve.doi.db.profile.api._
import com.pennsieve.doi.ServiceSpecHarness
import com.pennsieve.test.AwaitableImplicits
import org.scalatest.{ Matchers, WordSpec }

class CitationCacheMapperSpec
    extends WordSpec
    with ServiceSpecHarness
    with AwaitableImplicits
    with Matchers {

  "purge_citation_cache SQL function" should {

    "clear cache" in {

      ports.db
        .run(for {
          _ <- CitationCacheMapper.insertOrUpdate("10.1002/epi4.12260", None)
          _ <- CitationCacheMapper
            .insertOrUpdate("10.1093/brain/awaa200", Some("A citation"))
          _ <- CitationCacheMapper
            .insertOrUpdate("10.1126/science.169.3946.635", Some("A citation"))
        } yield ())
        .awaitFinite()

      ports.db.run(CitationCacheMapper.length.result).awaitFinite() shouldBe 3

      ports.db
        .run(sql"SELECT doi.purge_citation_cache()".as[Int])
        .awaitFinite()
        .toList shouldBe List(3)

      ports.db.run(CitationCacheMapper.length.result).awaitFinite() shouldBe 0
    }
  }
}
