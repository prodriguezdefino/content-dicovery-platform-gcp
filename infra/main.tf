
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

resource "google_project_service" "secret_service" {
  project = var.project
  service = "secretmanager.googleapis.com"
}

resource "google_project_service" "aiplatform_service" {
  project = var.project
  service = "aiplatform.googleapis.com"
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

resource "google_service_account_key" "sa_key" {
  service_account_id = google_service_account.dataflow_runner_sa.name
}

resource "google_secret_manager_secret" "sa_secret" {
  project    = var.project
  secret_id = var.run_name

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
  project_roles           = ["roles/dataflow.worker", "roles/storage.objectAdmin", "roles/pubsub.viewer", "roles/pubsub.subscriber", "roles/secretmanager.secretAccessor"]
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

resource "google_storage_bucket_object" "dummy_data" {
  name   = "embeddings-index-contents/dummy-data.json"
  bucket = google_storage_bucket.content.name
  content = <<EOF

EOF
}

resource "google_vertex_ai_index" "embeddings_index" {
  project  = var.project
  region   = var.region
  display_name = "${var.run_name}-embeddings-index"
  description = "A vertex ai matching engine index."
  metadata {
    contents_delta_uri = "gs://${google_storage_bucket.content.name}/embeddings-index-contents"
    config {
      dimensions = 2
      approximate_neighbors_count = 150
      distance_measure_type = "DOT_PRODUCT_DISTANCE"
      algorithm_config {
        tree_ah_config {
          leaf_node_embedding_count = 500
          leaf_nodes_to_search_percent = 7
        }
      }
    }
  }
  index_update_method = "STREAM_UPDATE"
  depends_on = [google_storage_bucket_object.dummy_data]
}

variable project {}

variable region {
  default = "us-central1"
}

variable run_name {}

output "df_sa" {
  value = google_service_account.dataflow_runner_sa.email
}

output "df_sa_id" {
  value = google_service_account.dataflow_runner_sa.unique_id
}