locals {
  service_name  = "${var.run_name}-service"
  configuration = <<EOL
      {
        "project.id" : "${var.project}",
        "region" : "${var.region}",
        "cloudrun.service.id" : "//run.googleapis.com/projects/${var.project}/locations/${var.region}/services/${local.service_name}",
        "pubsub.topic" : "${google_pubsub_topic.topic.id}",
        "bt.instance" : "${google_bigtable_instance.instance.name}",
        "bt.contexttable" : "${local.query_context_table_name}",
        "bt.contextcolumnfamily" : "${local.query_context_cf_name}",
        "bt.contextcolumnqualifier" : "exchanges",
        "bt.contenttable" : "${local.content_table_name}",
        "bt.contentcolumnfamily" : "${local.content_cf_name}",
        "bt.contentcolumnqualifier.content" : "content",
        "bt.contentcolumnqualifier.link" : "link",
        "matchingengine.index.id" : "${google_vertex_ai_index.embeddings_index.id}",
        "matchingengine.index.deployment" : "deploy${var.run_name}",
        "matchingengine.indexendpoint.id" : "${google_vertex_ai_index_endpoint.vertex_index_endpoint.id}",
        "matchingengine.indexendpoint.domain" : "${google_vertex_ai_index_endpoint.vertex_index_endpoint.public_endpoint_domain_name}",
        "bot.contextexpertise" : "${var.bot_context_expertise}",
        "bot.includeownknowledge" : "${var.bot_include_own_knowledge}",
        "service.account" : "${google_service_account.dataflow_runner_sa.email}",
        "embeddings_models" : ${jsonencode(var.embeddings_models)},
        "vector_storages" : ${jsonencode(var.vector_storages)},
        "llms" : ${jsonencode(var.llms)},
        "chunkers" : ${jsonencode(var.chunkers)},
        "alloy.ipAddress" : "${google_alloydb_instance.primary_instance.ip_address}",
        "alloy.databaseName" : "${var.alloy_db_name}",
        "alloy.username" : "${var.alloy_user}",
        "alloy.password" : "${random_password.alloy_password.result}",
        "alloy.schema" : "${var.alloy_schema_name}",
        "alloy.table" : "${var.alloy_table_name}"
      }
EOL
}

resource "google_secret_manager_secret" "service_config" {
  project   = var.project
  secret_id = "service-configuration-${var.run_name}"

  replication {
    auto {}
  }

  depends_on = [google_project_service.secret_service]
}

resource "google_secret_manager_secret_version" "service_config_version" {
  secret = google_secret_manager_secret.service_config.id

  secret_data = local.configuration
}

resource "google_cloud_run_v2_service" "services" {
  name     = local.service_name
  location = var.region
  project  = var.project
  ingress  = "INGRESS_TRAFFIC_ALL"

  template {
    service_account = google_service_account.dataflow_runner_sa.email
    containers {
      image = "${var.region}-docker.pkg.dev/${var.project}/content-dicovery-platform-${var.run_name}/${var.run_name}-services:latest"
      startup_probe {
        initial_delay_seconds = 5
        timeout_seconds       = 1
        period_seconds        = 3
        failure_threshold     = 3
        tcp_socket {
          port = 8080
        }
      }
      liveness_probe {
        initial_delay_seconds = 5
        timeout_seconds       = 1
        period_seconds        = 10
        failure_threshold     = 3
        http_get {
          path = "/q/health/live"
        }
      }
      env {
        name  = "JAVA_OPTS"
        value = "-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Dsecretmanager.configuration.version=${google_secret_manager_secret_version.service_config_version.name}"
      }
      resources {
        limits = {
          cpu    = "4"
          memory = "2Gi"
        }
      }
    }
  }

  traffic {
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
    percent = 100
  }

  lifecycle {
    ignore_changes = [
      launch_stage,
    ]
  }

  depends_on = [null_resource.build_and_push_services_image]
}

resource "google_cloud_run_service_iam_member" "cloudrun_permission_robotsa" {
  location = var.region
  project  = var.project
  service  = google_cloud_run_v2_service.services.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.dataflow_runner_sa.email}"
}

resource "google_cloud_run_service_iam_member" "additional_permissions" {
  location = var.region
  project  = var.project
  service  = google_cloud_run_v2_service.services.name
  role     = "roles/run.invoker"

  for_each = var.additional_authz_cloudrunservice
  member   = each.value
}

resource "google_cloud_run_service_iam_member" "workspace_domain_permission" {
  location = var.region
  project  = var.project
  service  = google_cloud_run_v2_service.services.name
  role     = "roles/run.invoker"
  member   = "domain:${var.workspace_domain}"
}

resource "google_api_gateway_api" "api_gateway" {
  project      = var.project
  provider     = google-beta
  api_id       = var.run_name
  display_name = "${var.run_name}-gateway-api"

  depends_on = [google_project_service.apigateway_service]
}

resource "google_api_gateway_api_config" "gateway_cfg" {
  project              = var.project
  provider             = google-beta
  api                  = google_api_gateway_api.api_gateway.api_id
  api_config_id_prefix = "${var.run_name}-config"

  gateway_config {
    backend_config {
      google_service_account = google_service_account.dataflow_runner_sa.email
    }
  }

  openapi_documents {
    document {
      path = "openapi.yaml"
      contents = base64encode(
        templatefile(
          "service-descriptors/openapi.yaml",
          {
            backend_service_url = google_cloud_run_v2_service.services.uri,
            gcloud_audiences    = var.gcloud_audiences
          }
        )
      )
    }
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "google_api_gateway_gateway" "gateway" {
  project      = var.project
  provider     = google-beta
  region       = var.region
  api_config   = google_api_gateway_api_config.gateway_cfg.id
  gateway_id   = var.run_name
  display_name = "${var.run_name}-gateway-service"

  depends_on = [google_api_gateway_api_config.gateway_cfg]
}