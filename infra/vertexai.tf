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

resource "null_resource" "create_index_endpoint" {

  triggers = {
    project     = var.project
    region      = var.region
    run_name    = var.run_name
    network     = local.network
    index_id    = google_vertex_ai_index.embeddings_index.id
    min_replica = 1
    max_replica = 5
  }

  provisioner "local-exec" {
    when       = create
    command    = "${path.module}/scripts/vertexai-endpoint-apply.sh ${self.triggers.project} ${self.triggers.region} ${self.triggers.run_name} ${self.triggers.network}"
    on_failure = fail
  }
  depends_on = [google_compute_global_address.vertex_range]
}

resource "null_resource" "create_index_deployment" {

  triggers = {
    project     = var.project
    region      = var.region
    run_name    = var.run_name
    network     = local.network
    index_id    = google_vertex_ai_index.embeddings_index.id
    min_replica = 1
    max_replica = 5
  }

  provisioner "local-exec" {
    when       = create
    command    = "${path.module}/scripts/vertexai-deploy-apply.sh ${self.triggers.project} ${self.triggers.region} ${self.triggers.run_name} ${self.triggers.network} ${self.triggers.index_id} ${self.triggers.min_replica} ${self.triggers.max_replica}"
    on_failure = fail
  }

  depends_on = [null_resource.create_index_endpoint, google_compute_global_address.vertex_range]
}

resource "null_resource" "delete_endpoint_deployment" {

  triggers = {
    endpoint    = null_resource.create_index_endpoint.id
    deployment  = null_resource.create_index_deployment.id
    project   = var.project
    region    = var.region
    run_name  = var.run_name
    network   = local.network
  }

  provisioner "local-exec" {
    when       = destroy
    command    = "${path.module}/scripts/vertexai-destroy.sh ${self.triggers.project} ${self.triggers.region} ${self.triggers.run_name}"
    on_failure = fail
  }

  depends_on = [null_resource.create_index_endpoint, null_resource.delete_endpoint_deployment]
}

data "external" "get_indexendpoint_domain" {
  program = ["${path.module}/scripts/retrieve-indexendpoint-domain.sh","${var.region}","${var.run_name}"]

  depends_on = [null_resource.delete_endpoint_deployment]
}

data "external" "get_indexendpoint_id" {
  program = ["${path.module}/scripts/retrieve-indexendpoint-id.sh","${var.region}","${var.run_name}"]

  depends_on = [null_resource.create_index_endpoint]
}

