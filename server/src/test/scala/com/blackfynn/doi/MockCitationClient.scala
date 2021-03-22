// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi

import com.pennsieve.doi.models.Citation
import com.pennsieve.doi.clients.CitationClient
import com.pennsieve.doi.logging.DoiLogContext
import com.pennsieve.doi.server.definitions._
import monocle.macros.syntax.lens._
import scala.concurrent.Future

class MockCitationClient() extends CitationClient {

  def getCitation(
    doi: String
  )(implicit
    logContext: DoiLogContext
  ): Future[Citation] =
    doi.toLowerCase match {
      case "10.1073/pnas.74.12.5463" =>
        Future.successful(Citation(doi, "A citation"))
      case "10.1073/missing-doi" =>
        Future.failed(CitationNotFound("not found"))
    }

}
