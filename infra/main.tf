terraform {
  backend "gcs" {
  }
  required_providers {
    google = {
        version = "5.43.1"
    }
    google-beta = {
        version = "5.43.1"
    }
  }
}

provider "google" {
  project = var.project
  region  = var.region
}
provider "google-beta" {
  project = var.project
  region  = var.region
}

/*       Local Variables     */
locals {
  topic_labels = {
    "app"        = "content_extraction"
    "run_name"   = "${var.run_name}"
  }
}

/*       resources           */

resource "google_project_service" "dataflow_service" {
  project = var.project
  service = "dataflow.googleapis.com"
  disable_on_destroy = false
}

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
resource "google_project_service" "sheets_service" {
  project = var.project
  service = "sheets.googleapis.com"
  disable_on_destroy = false
}
resource "google_project_service" "slides_service" {
  project = var.project
  service = "slides.googleapis.com"
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

resource "google_project_service" "iam_service" {
  project = var.project
  service = "iam.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "apigateway_service" {
  project = var.project
  service = "apigateway.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "servicemgmnt_service" {
  project = var.project
  service = "servicemanagement.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "svccntrl_service" {
  project = var.project
  service = "servicecontrol.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "policytroubleshooter_service" {
  project = var.project
  service = "policytroubleshooter.googleapis.com"
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
    "roles/bigtable.user",
    "roles/iam.serviceAccountUser",
    "roles/iam.serviceAccountTokenCreator",
    "roles/iam.securityReviewer"]
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

variable gcloud_audiences {
  default = "32555940559.apps.googleusercontent.com"
}

variable workspace_domain {
  default = "google.com"
}

variable additional_authz_cloudrunservice {
  description = "the values on the set should contain the special identifier prefixes, besides the actual email of the subject to grant the acess (example: 'serviceAccount:email' or 'user:email', etc)"
  type = set(string)
  default = []
}

variable bot_context_expertise {
  description = "sets the bot's expertise domain, this is directly used as part of the prompt generation on user query resolution."
  type = string
  default = ""
}

variable bot_include_own_knowledge {
  description = "sets if the bot should include its own knowledge to enrich responses."
  type = bool
  default = true
}

variable embeddings_models {
  description = "A list of embeddings models which will be used as part of the ingestion and query path."
  type = set(string)
  default = ["text-embedding-005"]
}

variable vector_storages {
  description = "A list of storage engines in use for embeddings vector searches."
  type = set(string)
  default = ["vector_search"]
}

variable chunkers {
  description = "A list of text chunking implementations to be used."
  type = set(string)
  default = ["gemini-2.0-flash"]
}

variable llms {
  description = "A list of LLM implementations to be used."
  type = set(string)
  default = ["gemini-2.0-flash"]
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
  value = google_vertex_ai_index_endpoint.vertex_index_endpoint.id
}

output "index_endpoint_domain" {
  value = google_vertex_ai_index_endpoint.vertex_index_endpoint.public_endpoint_domain_name
}

output "secret_service_configuration" {
  value = google_secret_manager_secret_version.service_config_version.name
}

output "backend_service_url" {
  value = google_cloud_run_v2_service.services.uri
}

output "service_url" {
  value = google_api_gateway_gateway.gateway.default_hostname
}