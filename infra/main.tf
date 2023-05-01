
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
}

resource "google_project_service" "docs_service" {
  project = var.project
  service = "docs.googleapis.com"
}

resource "google_pubsub_topic" "topic" {
  project = var.project
  name = var.run_name
}

resource "google_pubsub_subscription" "subscription" {
  project = var.project
  name  = "${var.run_name}-sub"
  topic = google_pubsub_topic.topic.name

  labels = local.topic_labels
}

resource "google_service_account" "dataflow_runner_sa" {
  project    = var.project
  account_id = "${var.run_name}-df-sa"
}

module "data_processing_project_membership_roles" {
  source                  = "terraform-google-modules/iam/google//modules/member_iam"
  service_account_address = google_service_account.dataflow_runner_sa.email
  project_id              = var.project
  project_roles           = ["roles/dataflow.worker", "roles/storage.objectAdmin", "roles/pubsub.viewer", "roles/pubsub.subscriber"]
}

resource "google_storage_bucket" "content" {
  project       = var.project 
  name          = "${var.run_name}-content-${var.project}"
  location      = "US-CENTRAL1"
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

variable run_name {}

output "df_sa" {
  value = google_service_account.dataflow_runner_sa.email
}

output "df_sa_id" {
  value = google_service_account.dataflow_runner_sa.unique_id
}