// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi

import com.pennsieve.auth.middleware.Jwt.Role.RoleIdentifier
import com.pennsieve.auth.middleware.{
  DatasetId,
  DatasetPermission,
  Jwt,
  OrganizationId,
  Permission,
  ServiceClaim
}
import com.pennsieve.auth.middleware.Validator.{
  hasDatasetAccess,
  hasOrganizationAccess
}
import com.pennsieve.models.Role
import shapeless.syntax.inject._

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

object Authenticator {

  def generateServiceToken(
    jwt: Jwt.Config,
    organizationId: Int,
    datasetId: Int,
    duration: FiniteDuration = 5.minutes
  ): Jwt.Token = {
    val claim = generateServiceClaim(organizationId, datasetId, duration)
    Jwt.generateToken(claim)(jwt)
  }

  def generateServiceClaim(
    organizationId: Int,
    datasetId: Int,
    duration: FiniteDuration = 5.minutes
  ): Jwt.Claim = {
    val serviceClaim = ServiceClaim(
      List(
        Jwt.OrganizationRole(
          OrganizationId(organizationId).inject[RoleIdentifier[OrganizationId]],
          Role.Owner
        ),
        Jwt.DatasetRole(
          DatasetId(datasetId).inject[RoleIdentifier[DatasetId]],
          Role.Owner
        )
      )
    )
    Jwt.generateClaim(serviceClaim, duration)
  }

  /*
   * Ensure that this claim has access to the given dataset
   */
  def withDatasetAccess[T](
    claim: Jwt.Claim,
    datasetId: Int,
    datasetPermission: Permission
  )(
    f: => Future[T]
  )(implicit
    executionContext: ExecutionContext
  ): Future[T] =
    if (hasDatasetAccess(claim, DatasetId(datasetId), datasetPermission))
      f
    else
      Future.failed(ForbiddenException(s"Not allowed for dataset $datasetId"))

  /*
   * Ensure that this claim has access to the given organization
   */
  def withOrganizationAccess[T](
    claim: Jwt.Claim,
    organizationId: Int
  )(
    f: => Future[T]
  )(implicit
    executionContext: ExecutionContext
  ): Future[T] =
    if (hasOrganizationAccess(claim, OrganizationId(organizationId))) f
    else
      Future.failed(
        ForbiddenException(s"Not allowed for organization $organizationId")
      )

  /*
   * Ensure that this claim has access to the organization and dataset
   */
  def withAuthorization[T](
    claim: Jwt.Claim,
    organizationId: Int,
    datasetId: Int,
    datasetPermission: Permission = DatasetPermission.ShowSettingsPage
  )(
    f: => Future[T]
  )(implicit
    executionContext: ExecutionContext
  ): Future[T] =
    withOrganizationAccess(claim, organizationId) {
      withDatasetAccess(claim, datasetId, datasetPermission) {
        f
      }
    }
}
