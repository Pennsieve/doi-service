// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.blackfynn.doi.clients

import com.blackfynn.doi.logging.DoiLogContext
import com.blackfynn.doi.models.Citation
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ Location, RawHeader }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.http.scaladsl.HttpExt
import com.blackfynn.doi.{
  CitationClientConfiguration,
  CitationException,
  CitationNotFound
}
import com.blackfynn.service.utilities.ContextLogger
import com.blackfynn.doi.server.definitions._
import monocle.macros.syntax.lens._

import io.circe.Json
import io.circe.syntax._

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Client for communicating with the DOI Content Negotiation API
  *
  * See https://citation.crosscite.org/docs.html
  */
trait CitationClient {

  def getCitation(
    doi: String
  )(implicit
    logContext: DoiLogContext
  ): Future[Citation]

}

object CitationClient {

  /**
    * Some citations (possibly from CrossRef?) format the DOI reference as
    * "doi:10.1093/brain/aww045" instead of a URL "https://doi.org/10.1093/brain/aww045"
    *
    * Try to normalize the citation to the hyperlink form. This DOI will be the
    * last thing in the citation. The frontend uses this to create a clickable
    * link.
    */
  def normalizeCitation(citation: String): String = {

    val DoiReferenceRegex = """doi:(10.[\d]+/[^\s]+)$""".r

    DoiReferenceRegex.replaceAllIn(
      citation.trim,
      found => s"https://doi.org/${found.group(1)}"
    )
  }
}

class CitationClientImpl(
  httpClient: HttpExt,
  citationClientConfig: CitationClientConfiguration
)(implicit
  executionContext: ExecutionContext,
  materializer: Materializer
) extends CitationClient {

  val log = new ContextLogger().context

  def getCitation(
    doi: String
  )(implicit
    logContext: DoiLogContext
  ): Future[Citation] =
    requestAndRedirect(
      Uri(s"${citationClientConfig.apiUrl}/$doi"),
      RawHeader("Accept", "text/x-bibliography; style=apa; locale=en-US")
    ).map(
      c => Citation(doi = doi, citation = CitationClient.normalizeCitation(c))
    )

  /**
    * The doi.org API delegates content negotiation to other servers; need to
    * follow redirects to get the final citation.
    */
  private def requestAndRedirect(
    uri: Uri,
    header: HttpHeader,
    maxRedirects: Int = 3
  )(implicit
    logContext: DoiLogContext
  ): Future[String] = {

    val request =
      HttpRequest(method = HttpMethods.GET, uri = uri).withHeaders(header)

    log.info(s"Getting citation with request ${request} ")

    maxRedirects match {
      case 0 => Future.failed(CitationException("Too many redirects"))
      case _ =>
        httpClient
          .singleRequest(request)
          .flatMap {
            case HttpResponse(StatusCodes.OK, _, entity, _) =>
              Unmarshal(entity)
                .to[String]

            case r @ HttpResponse(
                  StatusCodes.Found | StatusCodes.MovedPermanently |
                  StatusCodes.SeeOther,
                  _,
                  entity,
                  _
                ) =>
              for {
                _ <- entity.discardBytes().future
                location <- r.header[Location] match {
                  case Some(l) => Future.successful(l.uri)
                  case None =>
                    Future.failed(
                      CitationException("No 'Location' header in redirect")
                    )
                }
                _ = log.info(s"Redirecting to $location")
                redirectResponse <- requestAndRedirect(
                  location,
                  header,
                  maxRedirects - 1
                )
              } yield redirectResponse

            case HttpResponse(StatusCodes.NotFound, _, entity, _) =>
              Unmarshal(entity)
                .to[String]
                .flatMap(
                  msg =>
                    Future.failed(CitationNotFound("Could not find citation"))
                )

            case HttpResponse(_, _, entity, _) =>
              Unmarshal(entity)
                .to[String]
                .flatMap(msg => Future.failed(CitationException(msg)))
          }
    }
  }
}
