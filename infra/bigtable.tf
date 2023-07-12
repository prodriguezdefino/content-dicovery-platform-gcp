/*
* Copyright 2023 Google LLC. This software is provided as-is, without warranty
* or representation for any use or purpose. Your use of it is subject to your
* agreement with Google.
*/
locals {
  min_nodes  = 1
  max_nodes  = 5
  cpu_target = 80
  content_table_name = "content_per_embedding"
  content_cf_name    = "data"
  query_context_table_name = "query_context_by_session"
  query_context_cf_name    = "exchange"
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

resource "google_bigtable_table" "content_table" {
  project       = var.project
  name          = local.content_table_name
  instance_name = google_bigtable_instance.instance.name

  column_family {
    family = local.content_cf_name
  }
}

resource "google_bigtable_table" "context_table" {
  project       = var.project
  name          = local.query_context_table_name
  instance_name = google_bigtable_instance.instance.name

  column_family {
    family = local.query_context_cf_name
  }
}

resource "google_bigtable_gc_policy" "content_policy" {
  project         = var.project
  instance_name   = google_bigtable_instance.instance.name
  table           = google_bigtable_table.content_table.name
  column_family   = local.content_cf_name
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

resource "google_bigtable_gc_policy" "context_policy" {
  project         = var.project
  instance_name   = google_bigtable_instance.instance.name
  table           = google_bigtable_table.context_table.name
  column_family   = local.query_context_cf_name
  deletion_policy = "ABANDON"

  gc_rules = <<EOF
  {
    "mode": "union",
    "rules": [
      {
        "max_version": 50
      },
      {
        "max_age": "24h"
      }
    ]
  }
  EOF
}