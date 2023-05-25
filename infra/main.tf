provider "google-beta" {
}

/*       Local Variables     */
locals {
  topic_labels = {
    "app"        = "content_extraction"
    "run_name"   = "${var.run_name}"
  }
}

/*       resources           */

resource "google_project_service" "drive_service" {
  project = var.project
  service = "drive.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "docs_service" {
  project = var.project
  service = "docs.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "servicenetworking_service" {
  project = var.project
  service = "servicenetworking.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "secret_service" {
  project = var.project
  service = "secretmanager.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "aiplatform_service" {
  project = var.project
  service = "aiplatform.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "run_service" {
  project = var.project
  service = "run.googleapis.com"
  disable_on_destroy = false
}

resource "google_service_account" "dataflow_runner_sa" {
  project    = var.project
  account_id = "${var.run_name}-sa"
}

resource "google_service_account_key" "sa_key" {
  service_account_id = google_service_account.dataflow_runner_sa.name
}

resource "google_secret_manager_secret" "sa_secret" {
  project    = var.project
  secret_id  = var.run_name

  replication {
    automatic = true
  }

  depends_on = [google_project_service.secret_service]
}

resource "google_secret_manager_secret_version" "sa_secret_version" {
  secret = google_secret_manager_secret.sa_secret.id

  secret_data = google_service_account_key.sa_key.private_key
}

module "data_processing_project_membership_roles" {
  source                  = "terraform-google-modules/iam/google//modules/member_iam"
  service_account_address = google_service_account.dataflow_runner_sa.email
  project_id              = var.project
  project_roles           = [
    "roles/dataflow.worker", 
    "roles/storage.objectAdmin", 
    "roles/pubsub.viewer", 
    "roles/pubsub.publisher", 
    "roles/pubsub.subscriber", 
    "roles/secretmanager.secretAccessor", 
    "roles/aiplatform.user",
    "roles/bigtable.user"]
}

resource "google_storage_bucket" "content" {
  project       = var.project 
  name          = "${var.run_name}-content-${var.project}"
  location      = upper(var.region)
  storage_class = "REGIONAL"
  force_destroy = true

  lifecycle_rule {
    condition {
      age = 7
    }
    action {
      type = "Delete"
    }
  }
  public_access_prevention = "enforced"
}

variable project {}

variable region {
  default = "us-central1"
}

variable zone {
  default = "us-central1-a"
}

variable run_name {}

output "df_sa" {
  value = google_service_account.dataflow_runner_sa.email
}

output "df_sa_id" {
  value = google_service_account.dataflow_runner_sa.unique_id
}

output "index_id" {
  value = google_vertex_ai_index.embeddings_index.id
}

output "subnet" {
  value = data.google_compute_subnetwork.subnet_priv.self_link
}

output "index_endpoint_id" {
  value = data.external.get_indexendpoint_id.result.index_endpoint_id
}

output "index_endpoint_domain" {
  value = data.external.get_indexendpoint_domain.result.index_endpoint_domain
}

output "secret_credentials" {
  value = google_secret_manager_secret_version.sa_secret_version.name
}

output "secret_service_configuration" {
  value = google_secret_manager_secret_version.service_config_version.name
}

output "service_url" {
  value = google_cloud_run_v2_service.services.uri
}
