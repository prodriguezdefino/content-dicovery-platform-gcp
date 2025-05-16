
data "google_compute_network" "default_network" {
  name    = "default"
  project = var.project
}

# AlloyDB Cluster
resource "google_alloydb_cluster" "default_cluster" {
  project    = var.project
  location   = var.region
  cluster_id = "rag-${var.run_name}"
  network    = data.google_compute_network.default_network.self_link
  initial_user {
    user     = var.alloy_user
    password = var.pass
  }
  deletion_policy = "FORCE"

  depends_on = [
    google_project_service.alloydb_api,
  ]
}

# AlloyDB Instance
resource "google_alloydb_instance" "primary_instance" {
  cluster     = google_alloydb_cluster.default_cluster.name
  instance_id = "alloydb-instance-rag"
  instance_type = "PRIMARY"

  machine_config {
    cpu_count = 2 # Minimum allowed
  }

  availability_type = "REGIONAL" # Or "ZONAL"
  depends_on = [google_alloydb_cluster.default_cluster]
}
