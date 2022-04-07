// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi

import java.time.OffsetDateTime

import com.pennsieve.doi.clients.DataCiteClient
import com.pennsieve.doi.logging.DoiLogContext
import com.pennsieve.doi.models.{
  Contributor,
  Creator,
  DataciteDoi,
  Description,
  DoiEvent,
  DoiState,
  RelatedIdentifier,
  RelationType,
  Rights,
  Title
}
import com.pennsieve.doi.server.definitions._
import monocle.macros.syntax.lens._
import scala.concurrent.Future

class MockDataCiteClient() extends DataCiteClient {
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
    Contributor("dataset", "owner", None, Some("9999-8888-7777-6666"))
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
      owner = testDoiContributors,
      publisher = Some("Pennsieve Discover")
    )

  override def createDoi(
    doiSuffix: String,
    title: String,
    creators: List[CreatorDTO],
    publicationYear: Option[Int],
    version: Option[Int],
    description: Option[String],
    licenses: Option[List[LicenseDTO]],
    owner: Option[CreatorDTO]
  )(implicit
    logContext: DoiLogContext
  ): Future[DataciteDoi] =
    // Using this to simulate failure condition in the client
    if (title == existingDoiTitle) {
      Future.failed(new Throwable("DOI already exists"))
    } else {
      Future.successful(testDoi)
    }

  override def getDoi(
    doi: String
  )(implicit
    logContext: DoiLogContext
  ): Future[DataciteDoi] = {
    Future.successful(testDoi)
  }

  override def publishDoi(
    doi: String,
    title: String,
    creators: List[CreatorDTO],
    publicationYear: Int,
    url: String,
    publisher: Option[String],
    version: Option[Int],
    description: Option[String],
    licenses: Option[List[LicenseDTO]],
    owner: Option[CreatorDTO],
    collections: Option[List[CollectionDTO]],
    externalPublications: Option[List[ExternalPublicationDTO]]
  )(implicit
    logContext: DoiLogContext
  ): Future[DataciteDoi] = {

    Future.successful(
      DataciteDoi(
        doi = doi,
        creators = creators.map { c =>
          Creator(c.firstName, c.lastName, c.middleInitial, c.orcid)
        },
        title = title,
        publicationYear = Some(publicationYear),
        version = version,
        owner = owner
          .map(
            o => Contributor(o.firstName, o.lastName, o.middleInitial, o.orcid)
          ),
        descriptions = description.map(d => List(Description(d))),
        rightsList = licenses.getOrElse(List[LicenseDTO]()).map { l =>
          Rights(l.license, Some(l.licenseUri))
        },
        relatedIdentifiers = List.empty,
        url = Some(url),
        publisher = publisher,
        state = DoiState.Findable,
        event = Some(DoiEvent.Publish),
        mode = "edit"
      )
    )
  }

  override def reviseDoi(
    doi: String,
    title: String,
    creators: List[CreatorDTO],
    version: Option[Int],
    description: Option[String],
    licenses: Option[List[LicenseDTO]],
    owner: Option[CreatorDTO],
    collections: Option[List[CollectionDTO]],
    externalPublications: Option[List[ExternalPublicationDTO]],
    updated: Option[OffsetDateTime]
  )(implicit
    logContext: DoiLogContext
  ): Future[DataciteDoi] = {

    Future.successful(
      testDoi
        .lens(_.data.attributes)
        .modify(
          _.copy(
            titles = List(Title(title)),
            creators = creators.map { c =>
              Creator(c.firstName, c.lastName, c.middleInitial, c.orcid)
            },
            state = Some(DoiState.Findable),
            version = version,
            contributors = owner
              .map(
                o =>
                  List(
                    Contributor(
                      o.firstName,
                      o.lastName,
                      o.middleInitial,
                      o.orcid
                    )
                  )
              ),
            descriptions = description.map(d => List(Description(d))),
            rightsList = Some(licenses.getOrElse(List[LicenseDTO]()).map { l =>
              Rights(l.license, Some(l.licenseUri))

            }),
            relatedIdentifiers = Some(List.empty),
            event = None
          )
        )
    )
  }

  override def hideDoi(
    doi: String
  )(implicit
    logContext: DoiLogContext
  ): Future[DataciteDoi] = {
    Future.successful(
      testDoi
        .lens(_.data.attributes)
        .modify(
          _.copy(
            state = Some(DoiState.Registered),
            event = Some(DoiEvent.Hide),
            mode = Some("edit")
          )
        )
    )
  }
}
