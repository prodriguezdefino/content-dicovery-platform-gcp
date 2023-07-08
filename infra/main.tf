terraform {
  backend "gcs" {
  }
}

provider "google-beta" {
  project = var.project
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
  public_access_prevention = "enforced"
  uniform_bucket_level_access = true
}

variable project {}

variable run_name {}

variable region {
  default = "us-central1"
}

variable zone {
  default = "us-central1-a"
}

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
  value = google_compute_subnetwork.subnet_priv.self_link
}

output "index_endpoint_id" {
  value = data.external.get_indexendpoint_id.result.index_endpoint_id
}

output "index_endpoint_domain" {
  value = data.external.get_indexendpoint_domain.result.index_endpoint_domain
}

output "secret_service_configuration" {
  value = google_secret_manager_secret_version.service_config_version.name
}

output "service_url" {
  value = google_cloud_run_v2_service.services.uri
}
