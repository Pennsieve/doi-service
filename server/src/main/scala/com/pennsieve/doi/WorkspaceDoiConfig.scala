// Copyright (c) 2021 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi

import com.typesafe.config.{ Config => TypesafeConfig, ConfigFactory, ConfigUtil }

import scala.jdk.CollectionConverters._

/**
  * What the DOIs of a Discover workspace say beyond each dataset's own
  * metadata.
  *
  * @param fieldsOfScience OECD Fields of Science and Technology, as DataCite
  *                        names them ("Basic medicine" becomes the subject
  *                        "FOS: Basic medicine").
  * @param subjects        plain subjects every DOI from the workspace gets.
  */
case class WorkspaceDoiSettings(
  fieldsOfScience: List[String] = List.empty,
  subjects: List[String] = List.empty
)

/**
  * The workspaces' DOI settings for one environment, from
  * doi-workspaces.json: a default, and settings by organization id. A
  * workspace without fields of science gets the default's, so every DOI has at
  * least one.
  */
case class WorkspaceDoiConfig(
  default: WorkspaceDoiSettings,
  workspaces: Map[Int, WorkspaceDoiSettings]
) {
  def forWorkspace(organizationId: Int): WorkspaceDoiSettings = {
    val settings = workspaces.getOrElse(organizationId, default)
    if (settings.fieldsOfScience.isEmpty)
      settings.copy(fieldsOfScience = default.fieldsOfScience)
    else settings
  }
}

object WorkspaceDoiConfig {

  val Resource = "doi-workspaces.json"

  /** The settings for an environment (prod, dev); without one, the default. */
  def load(
    environment: Option[String],
    config: TypesafeConfig = ConfigFactory.parseResources(Resource)
  ): WorkspaceDoiConfig = {
    val workspaces = environment
      .filter(env => config.hasPath(ConfigUtil.joinPath(env)))
      .map { env =>
        val section = config.getConfig(ConfigUtil.joinPath(env))
        section.root.keySet.asScala.toList.map { id =>
          id.toInt -> settings(section.getConfig(ConfigUtil.joinPath(id)))
        }.toMap
      }
      .getOrElse(Map.empty)
    WorkspaceDoiConfig(settings(config.getConfig("default")), workspaces)
  }

  private def settings(c: TypesafeConfig): WorkspaceDoiSettings =
    WorkspaceDoiSettings(
      fieldsOfScience = strings(c, "fieldsOfScience"),
      subjects = strings(c, "subjects")
    )

  private def strings(c: TypesafeConfig, path: String): List[String] =
    if (c.hasPath(path)) c.getStringList(path).asScala.toList else List.empty
}
