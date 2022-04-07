// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi.handlers

import akka.stream.ActorMaterializer

import com.pennsieve.doi.server.healthcheck.{
  HealthcheckHandler => GuardrailHandler,
  HealthcheckResource
}

import scala.concurrent.Future

class HealthcheckHandler extends GuardrailHandler {

  override def healthcheck(
    respond: HealthcheckResource.healthcheckResponse.type
  )(
  ): Future[HealthcheckResource.healthcheckResponse] = {
    Future.successful(HealthcheckResource.healthcheckResponseOK)
  }
}

object HealthcheckHandler {
  def routes(
    implicit
    materializer: ActorMaterializer
  ) = HealthcheckResource.routes(new HealthcheckHandler)
}
