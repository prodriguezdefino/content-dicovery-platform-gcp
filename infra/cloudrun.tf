locals {
    configuration = <<EOL
      {
        "project.id" : "${var.project}",
        "region" : "${var.region}",
        "bt.instance" : "${google_bigtable_instance.instance.name}",
        "bt.table" : "content_per_embedding",
        "bt.columnfamily" : "data",
        "bt.columnqualifier.content" : "content",
        "bt.columnqualifier.link" : "link",
        "secretmanager.credentials.id" : "${google_secret_manager_secret_version.sa_secret_version.name}",
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