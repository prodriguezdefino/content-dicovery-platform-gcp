data "google_project" "project" {
  project_id=var.project
}

locals {
  dimensions = 768
  network = "projects/${data.google_project.project.number}/global/networks/${google_compute_network.net_priv.name}"
  embeddings = jsonencode(range(local.dimensions))
}

resource "google_storage_bucket_object" "dummy_data" {
  name   = "embeddings-index-contents/dummy-data.json"
  bucket = google_storage_bucket.content.name
  content = <<EOF
{"id": "dummy", "embedding": ${local.embeddings}}
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
      dimensions = local.dimensions
      approximate_neighbors_count = 150
      distance_measure_type = "DOT_PRODUCT_DISTANCE"
      feature_norm_type = "UNIT_L2_NORM"
      algorithm_config {
        tree_ah_config {
          leaf_node_embedding_count = 1000
          leaf_nodes_to_search_percent = 10
        }
      }
    }
  }
  index_update_method = "STREAM_UPDATE"
  depends_on = [google_storage_bucket_object.dummy_data]
}

resource "google_vertex_ai_index_endpoint" "vertex_index_endpoint" {
  project      = var.project
  display_name = "endpoint-${var.run_name}"
  description  = "The endpoint for vector search"
  region       = var.region
  public_endpoint_enabled = true
}

resource "google_vertex_ai_index_endpoint_deployed_index" "basic_deployed_index" {
  index_endpoint        = google_vertex_ai_index_endpoint.vertex_index_endpoint.id
  index                 = google_vertex_ai_index.embeddings_index.id
  deployed_index_id     = "deploy${var.run_name}"
  enable_access_logging = false
  display_name          = "deploy${var.run_name}"

  automatic_resources {
    min_replica_count = 1
    max_replica_count = 4
  }

  depends_on = [ google_vertex_ai_index_endpoint.vertex_index_endpoint]
}
