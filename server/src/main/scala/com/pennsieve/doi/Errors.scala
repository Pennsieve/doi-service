// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi

import com.pennsieve.doi.models.DataciteError

case class NoDoiException(doi: String) extends Throwable {
  override def getMessage: String = s"No doi could be found for doi=$doi"
}

case class NoDatasetDoiException(organizationid: Int, datasetId: Int)
    extends Throwable {
  override def getMessage: String =
    s"No doi could be found for organizationId=$organizationid datasetId=$datasetId"
}

case object DuplicateDoiException extends Throwable

case class ForbiddenException(msg: String) extends Throwable {}

case class DataciteException(error: DataciteError) extends Throwable {
  override def getMessage: String =
    s"Datacite error: ${error.errors.map(_.toString).mkString("\n")}"
}

case class CitationNotFound(msg: String) extends Throwable {
  override def getMessage: String = msg
}

case class CitationException(msg: String) extends Throwable {
  override def getMessage: String =
    s"Citation error: ${msg}"
}
