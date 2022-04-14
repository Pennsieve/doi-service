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
    respond: HealthcheckResource.HealthcheckResponse.type
  )(
  ): Future[HealthcheckResource.HealthcheckResponse] = {
    Future.successful(HealthcheckResource.HealthcheckResponseOK)
  }
}

object HealthcheckHandler {
  def routes(
    implicit
    system: ActorSystem
  ) = HealthcheckResource.routes(new HealthcheckHandler)
}
