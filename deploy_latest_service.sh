#!/bin/bash
set -eu

if [ "$#" -ne 3 ] 
  then
    echo "Usage : sh deploy_latest_service.sh <gcp project> <a run name> <region>" 
    exit -1
fi

GCP_PROJECT=$1
RUN_NAME=$2
GCP_REGION=$3

pushd services

mvn clean install -DskipTests &&  sh create_container.sh $GCP_PROJECT $RUN_NAME $GCP_REGION

popd 

gcloud run services update $RUN_NAME-service \
    --project $GCP_PROJECT \
    --region $GCP_REGION \
    --image "gcr.io/${GCP_PROJECT}/${GCP_REGION}/$RUN_NAME-services:latest"