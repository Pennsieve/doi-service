variable "aws_account" {}

variable "environment_name" {}

variable "service_name" {}

variable "vpc_name" {}

variable "doi_postgres_host" {}

variable "doi_postgres_db" {
  default = "doi_postgres"
}

variable "doi_postgres_num_connections" {
  default = "10"
}

variable "doi_postgres_queue_size" {
  default = "1000"
}

variable "datacite_client_username" {}

variable "datacite_api_url" {}

variable "datacite_pennsieve_prefix" {}

variable "ecs_task_iam_role_id" {}

# Java Opts
variable "newrelic_agent_enabled" {
  default = "true"
}

locals {
  java_opts = [
    "-javaagent:/app/newrelic.jar",
    "-Dnewrelic.config.agent_enabled=${var.newrelic_agent_enabled}",
  ]

  service = element(split("-", var.service_name), 0)
  tier    = element(split("-", var.service_name), 1)
}
