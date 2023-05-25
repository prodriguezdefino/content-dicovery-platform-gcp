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
