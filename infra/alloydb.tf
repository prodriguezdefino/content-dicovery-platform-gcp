# AlloyDB Cluster
resource "google_alloydb_cluster" "default_cluster" {
  project    = var.project
  location   = var.region
  cluster_id = "alloydb-cluster-rag"
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
