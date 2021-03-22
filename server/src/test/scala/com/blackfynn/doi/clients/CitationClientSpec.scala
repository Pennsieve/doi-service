// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi.clients

import com.pennsieve.doi.models.Doi
import com.pennsieve.doi.{ NoDoiException, ServiceSpecHarness, TestUtilities }
import com.pennsieve.test.AwaitableImplicits
import org.scalatest.{ Matchers, WordSpec }

class CitationClientSpec extends WordSpec with Matchers {

  "CitationClient" should {

    "normalize DOI references" in {
      CitationClient.normalizeCitation(
        "Sanger, F... 5463–5467. doi:10.1073/pnas.74.12.5463"
      ) shouldBe "Sanger, F... 5463–5467. https://doi.org/10.1073/pnas.74.12.5463"
    }

    "ignore citations with DOI URLS" in {
      CitationClient.normalizeCitation(
        "Sanger, F... 5463–5467. https://doi.org/10.1073/pnas.74.12.5463"
      ) shouldBe "Sanger, F... 5463–5467. https://doi.org/10.1073/pnas.74.12.5463"
    }

    "ignore other 'doi:...' strings in the citation" in {
      CitationClient.normalizeCitation(
        "Sanger, doi:10.1234/asdf ... 5463–5467. doi:10.1073/pnas.74.12.5463"
      ) shouldBe "Sanger, doi:10.1234/asdf ... 5463–5467. https://doi.org/10.1073/pnas.74.12.5463"
    }
  }
}
