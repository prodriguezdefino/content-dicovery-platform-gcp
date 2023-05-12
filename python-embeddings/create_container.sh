#!/bin/bash
set -xeu
if [ "$#" -ne 1 ] && [ "$#" -ne 2 ]
  then
    echo "Usage : sh create-template.sh <gcp project> <gcp region>" 
    exit -1
fi

GCP_PROJECT=$1

if [ "$#" -eq 1 ] 
  then
    GCP_REGION="us-central1"
  else
    GCP_REGION=$2
fi

export DOCKER_IMAGE="gcr.io/${GCP_PROJECT}/${GCP_REGION}/beam-embeddings:latest"

gcloud auth configure-docker
# Build Docker Image
docker image build --progress=plain -t $DOCKER_IMAGE .
# Push image to Google Cloud Registry
docker push $DOCKER_IMAGE