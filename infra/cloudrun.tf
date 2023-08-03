locals {
    service_name = "${var.run_name}-service"
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
        "matchingengine.indexendpoint.id" : "${data.external.get_indexendpoint_id.result.index_endpoint_id}",
        "matchingengine.indexendpoint.domain" : "${data.external.get_indexendpoint_domain.result.index_endpoint_domain}"
      }
EOL
}

resource "google_secret_manager_secret" "service_config" {
  project   = var.project
  secret_id = "service-configuration-${var.run_name}"

  replication {
    automatic = true
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
      image = "gcr.io/${var.project}/${var.region}/${var.run_name}-services:latest"
      startup_probe {
          initial_delay_seconds = 5
          timeout_seconds = 1
          period_seconds = 3
          failure_threshold = 3
          tcp_socket {
            port = 8080
          }
        }
        liveness_probe {
          initial_delay_seconds = 5
          timeout_seconds = 1
          period_seconds = 10
          failure_threshold = 3
          http_get {
            path = "/q/health/live"
          }
        }
        env {
            name = "JAVA_OPTS_APPEND"
            value = "-Dsecretmanager.configuration.version=${google_secret_manager_secret_version.service_config_version.name}"
        }
      resources {
        limits = {
          cpu = "2"
          memory = "2Gi"
        }
      }
    }
  }

  traffic {
    type = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
    percent         = 100
  }

  lifecycle {
    ignore_changes = [
      launch_stage,
    ]
  }
}

resource "google_cloud_run_service_iam_member" "cloudrun_permission_robotsa" {
  location = var.region
  project = var.project
  service = google_cloud_run_v2_service.services.name
  role = "roles/run.invoker"
  member = "serviceAccount:${google_service_account.dataflow_runner_sa.email}"
}

resource "google_cloud_run_service_iam_member" "additional_permissions" {
  location = var.region
  project = var.project
  service = google_cloud_run_v2_service.services.name
  role = "roles/run.invoker"
  
  for_each = var.additional_authz_cloudrunservice
  member = each.value
}

resource "google_cloud_run_service_iam_member" "workspace_domain_permission" {
  location = var.region
  project = var.project
  service = google_cloud_run_v2_service.services.name
  role = "roles/run.invoker"
  member = "domain:${var.workspace_domain}"
}

resource "google_api_gateway_api" "api_gateway" {
  project = var.project
  provider = google-beta
  api_id       = var.run_name
  display_name = "${var.run_name}-gateway-api"

  depends_on = [google_project_service.apigateway_service]
}

resource "google_api_gateway_api_config" "gateway_cfg" {
  project = var.project
  provider = google-beta
  api           = google_api_gateway_api.api_gateway.api_id
  api_config_id = "${var.run_name}-config"

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
            gcloud_audiences = var.gcloud_audiences
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
