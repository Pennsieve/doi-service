// DATABASE (POSTGRES) CONFIGURATION

resource "aws_ssm_parameter" "doi_postgres_host" {
  name = "/${var.environment_name}/${var.service_name}/doi-postgres-host"
  type = "String"
  value = var.doi_postgres_host
}

resource "aws_ssm_parameter" "doi_postgres_db" {
  name = "/${var.environment_name}/${var.service_name}/doi-postgres-db"
  type = "String"
  value = var.doi_postgres_db
}

resource "aws_ssm_parameter" "doi_postgres_user" {
  name  = "/${var.environment_name}/${var.service_name}/doi-postgres-user"
  type  = "String"
  value = "${var.environment_name}_${replace(var.service_name, "-", "_")}_user"
}

resource "aws_ssm_parameter" "doi_postgres_password" {
  name      = "/${var.environment_name}/${var.service_name}/doi-postgres-password"
  overwrite = false
  type      = "SecureString"
  value     = "dummy"

  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "doi_postgres_num_connections" {
  name  = "/${var.environment_name}/${var.service_name}/doi-postgres-num-connections"
  type  = "String"
  value = var.doi_postgres_num_connections
}

resource "aws_ssm_parameter" "doi_postgres_queue_size" {
  name  = "/${var.environment_name}/${var.service_name}/doi-postgres-queue-size"
  type  = "String"
  value = var.doi_postgres_queue_size
}

// DATACITE CONFIGURATION
resource "aws_ssm_parameter" "datacite_client_username" {
  name  = "/${var.environment_name}/${var.service_name}/datacite-client-username"
  type  = "String"
  value = var.datacite_client_username
}

resource "aws_ssm_parameter" "datacite_client_password" {
  name      = "/${var.environment_name}/${var.service_name}/datacite-client-password"
  overwrite = false
  type      = "SecureString"
  value     = "dummy"

  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "datacite_api_url" {
  name  = "/${var.environment_name}/${var.service_name}/datacite-api-url"
  type  = "String"
  value = var.datacite_api_url
}

resource "aws_ssm_parameter" "datacite_pennsieve_prefix" {
  name  = "/${var.environment_name}/${var.service_name}/datacite-pennsieve-prefix"
  type  = "String"
  value = var.datacite_pennsieve_prefix
}

// JWT CONFIGURATION
resource "aws_ssm_parameter" "doi_jwt_secret_key" {
  name      = "/${var.environment_name}/${var.service_name}/doi-jwt-secret-key"
  overwrite = false
  type      = "SecureString"
  value     = "dummy"

  lifecycle {
    ignore_changes = [value]
  }
}

# // NEW RELIC CONFIGURATION
# resource "aws_ssm_parameter" "java_opts" {
#   name  = "/${var.environment_name}/${var.service_name}/java-opts"
#   type  = "String"
#   value = "${join(" ", local.java_opts)}"
# }

# resource "aws_ssm_parameter" "new_relic_app_name" {
#   name  = "/${var.environment_name}/${var.service_name}/new-relic-app-name"
#   type  = "String"
#   value = "${var.environment_name}-${var.service_name}"
# }

# resource "aws_ssm_parameter" "new_relic_labels" {
#   name  = "/${var.environment_name}/${var.service_name}/new-relic-labels"
#   type  = "String"
#   value = "Environment:${var.environment_name};Service:${local.service};Tier:${local.tier}"
# }

# resource "aws_ssm_parameter" "new_relic_license_key" {
#   name      = "/${var.environment_name}/${var.service_name}/new-relic-license-key"
#   overwrite = false
#   type      = "SecureString"
#   value     = "dummy"

#   lifecycle {
#     ignore_changes = ["value"]
#   }
# }
