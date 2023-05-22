#!/bin/bash
set -x

if [ "$#" -ne 4 ] 
  then
    echo "Usage : sh vertexai-apply.sh <gcp project> <region> <run name> <network>"
    exit -1
fi

PROJECT=$1
REGION=$2
RUN_NAME=$3
NETWORK=$4
DEPLOYMENT=deployment$RUN_NAME


# retrieve the recently created index id
FULL_INDEX_ENDPOINT_ID=$(gcloud beta ai index-endpoints list --region us-central1 --filter="network=$NETWORK" --format='value(name)')

if [ -n "${FULL_INDEX_ENDPOINT_ID}" ]; then
    INDEX_ENDPOINT_ID=$(basename $FULL_INDEX_ENDPOINT_ID)
    # undeploy the index from the endpoint
    gcloud ai index-endpoints undeploy-index $INDEX_ENDPOINT_ID --deployed-index-id=$DEPLOYMENT --project=$PROJECT --region=$REGION

    # delete the index endpoint
    gcloud ai index-endpoints delete $INDEX_ENDPOINT_ID --project=$PROJECT --region=$REGION
fi

