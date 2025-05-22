resource "google_compute_global_address" "private_ip_alloc_alloydb" {
  project       = var.project
  name          = "private-ip-alloc-alloydb-${var.run_name}"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  ip_version    = "IPV4"
  address       = "10.100.0.0"
  prefix_length = 20
  network       = google_compute_network.net_priv.id

  depends_on = [google_compute_network.net_priv]
}

resource "google_service_networking_connection" "alloydb_vpc_connection" {
  network                 = google_compute_network.net_priv.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_ip_alloc_alloydb.name]

  depends_on = [
    google_project_service.servicenetworking_service,
    google_compute_global_address.private_ip_alloc_alloydb
  ]
}


# AlloyDB Cluster
resource "google_alloydb_cluster" "default_cluster" {
  project    = var.project
  location   = var.region
  cluster_id = "rag-${var.run_name}"
  network_config {
    network = google_compute_network.net_priv.id
  }

  initial_user {
    user     = var.alloy_user
    password = var.alloy_password
  }
  deletion_policy = "FORCE"

  depends_on = [
    google_service_networking_connection.alloydb_vpc_connection
  ]
}

# AlloyDB Instance
resource "google_alloydb_instance" "primary_instance" {
  cluster     = google_alloydb_cluster.default_cluster.name
  instance_id = "alloydb-instance-rag"
  instance_type = "PRIMARY"

  machine_config {
    cpu_count = 2 #
  }

  availability_type = "REGIONAL"
  depends_on = [google_alloydb_cluster.default_cluster]
}
