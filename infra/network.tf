resource "google_compute_network" "net_priv" {
  name                    = "network-${var.run_name}"
  auto_create_subnetworks = false
  project                 = "${var.project}"
}

resource "google_compute_subnetwork" "subnet_priv" {
  name                     = "${var.region}-subnet-${var.run_name}"
  project                  = "${var.project}"
  region                   = "${var.region}"
  private_ip_google_access = true
  ip_cidr_range            = "10.0.0.0/24"
  network                  = "${google_compute_network.net_priv.self_link}"
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
