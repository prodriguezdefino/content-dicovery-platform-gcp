#!/bin/bash
set -eu
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

DOCKER_PYTHON_IMAGE="gcr.io/${GCP_PROJECT}/${GCP_REGION}/beam-embeddings:latest"

gcloud builds submit --tag $DOCKER_PYTHON_IMAGE