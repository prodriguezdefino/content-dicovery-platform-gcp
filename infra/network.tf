resource "google_compute_network" "net_priv" {
  name                    = "network-${var.run_name}"
  auto_create_subnetworks = true
  project                 = "${var.project}"
}

resource "google_compute_router" "router" {
  name    = "net-router-${var.run_name}"
  project = var.project
  region  = var.region
  network = "${google_compute_network.net_priv.self_link}"

  bgp {
    asn = 64514
  }
}

resource "google_compute_router_nat" "nat" {
  name                               = "nat-${var.run_name}"
  project                            = var.project
  router                             = "${google_compute_router.router.name}"
  region                             = "${var.region}"
  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"
}

resource "google_service_networking_connection" "vertex_vpc_connection" {
  network                 = google_compute_network.net_priv.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.vertex_range.name]
  depends_on = [google_project_service.servicenetworking_service]
}

resource "google_compute_global_address" "vertex_range" {
  name          = "vertex-peering"
  project       = var.project
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.net_priv.id
}

data "google_compute_subnetwork" "subnet_priv" {
  project = var.project
  name    = "network-${var.run_name}"
  region  = var.region
}


resource "null_resource" "subnet_privateaccess" {

  triggers = {
    region      = var.region
    subnet      = data.google_compute_subnetwork.subnet_priv.name
  }

  provisioner "local-exec" {
    when       = create
    command    = "gcloud compute networks subnets update ${self.triggers.subnet} --region=${self.triggers.region} --enable-private-ip-google-access --quiet"
    on_failure = fail
  }

}

resource "google_compute_firewall" "allow_icmp" {
  name    = "allow-icmp-${var.run_name}"
  project                            = var.project
  network = "${google_compute_network.net_priv.name}"

  allow {
    protocol = "icmp"
  }

  source_ranges = ["0.0.0.0/0"]
}

resource "google_compute_firewall" "allow_all_internal" {
  name    = "allow-all-internal-${var.run_name}"
  project                            = var.project
  network = "${google_compute_network.net_priv.name}"

  allow {
    protocol = "all"
  }

  source_ranges = ["10.128.0.0/9"]
}

resource "google_compute_firewall" "allow_rdp" {
  name    = "allow-rdp-${var.run_name}"
  project                            = var.project
  network = "${google_compute_network.net_priv.name}"

  allow {
    protocol = "tcp"
    ports    = ["3389"]
  }

  source_ranges = ["0.0.0.0/0"]
}

resource "google_compute_firewall" "allow_ssh" {
  name    = "allow-ssh-${var.run_name}"
  project                            = var.project
  network = "${google_compute_network.net_priv.name}"

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  source_ranges = ["0.0.0.0/0"]
}
