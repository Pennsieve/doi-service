// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi.logging

import com.pennsieve.service.utilities.LogContext

final case class DoiLogContext(
  organizationId: Option[Int] = None,
  datasetId: Option[Int] = None,
  userId: Option[Int] = None,
  doi: Option[String] = None,
  doiId: Option[Int] = None
) extends LogContext {
  override val values: Map[String, String] = inferValues(this)
}
