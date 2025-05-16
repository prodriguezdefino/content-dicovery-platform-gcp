resource "google_artifact_registry_repository" "repo" {
  location      = var.region
  project       = var.project
  repository_id = "content-dicovery-platform-${var.run_name}"
  format        = "DOCKER"
}

resource "null_resource" "build_and_push_services_image" {
  triggers = {
    docker_file = "${filesha1("../services/Dockerfile")}"
  }

  provisioner "local-exec" {
    command     = <<EOF
      gcloud builds submit --tag ${var.region}-docker.pkg.dev/${var.project}/content-dicovery-platform-${var.run_name}/${var.run_name}-services:latest --project ${var.project} --region ${var.region}

    EOF
    working_dir = "../services"
  }

  depends_on = [google_artifact_registry_repository.repo]
}
