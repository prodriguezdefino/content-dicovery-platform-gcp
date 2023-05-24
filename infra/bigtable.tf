/*
* Copyright 2023 Google LLC. This software is provided as-is, without warranty
* or representation for any use or purpose. Your use of it is subject to your
* agreement with Google.
*/
locals {
  min_nodes  = 1
  max_nodes  = 5
  cpu_target = 80
  table_name = "content_per_embedding"
  cf_name    = "data"
}

/*       resources           */
resource "google_bigtable_instance" "instance" {
  name    = "${var.run_name}-instance"
  project = var.project

  cluster {
    cluster_id   = "${var.run_name}-cluster"
    zone         = var.zone
    storage_type = "SSD"

    autoscaling_config {
      min_nodes  = "${local.min_nodes}"
      max_nodes  = "${local.max_nodes}"
      cpu_target = "${local.cpu_target}"
    }
  }
  
  deletion_protection=false
}

resource "google_bigtable_table" "table" {
  project       = var.project
  name          = local.table_name
  instance_name = google_bigtable_instance.instance.name

  column_family {
    family = local.cf_name
  }
}

resource "google_bigtable_gc_policy" "policy" {
  project         = var.project
  instance_name   = google_bigtable_instance.instance.name
  table           = google_bigtable_table.table.name
  column_family   = local.cf_name
  deletion_policy = "ABANDON"


  gc_rules = <<EOF
  {
    "rules": [
      {
        "max_version": 5
      }
    ]
  }
  EOF
}