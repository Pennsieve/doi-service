// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi.handlers

import akka.actor.ActorSystem
import com.pennsieve.doi.server.healthcheck.{
  HealthcheckResource,
  HealthcheckHandler => GuardrailHandler
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
    system: ActorSystem
  ) = HealthcheckResource.routes(new HealthcheckHandler)
}
