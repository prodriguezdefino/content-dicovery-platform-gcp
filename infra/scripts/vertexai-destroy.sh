#!/bin/bash
set -x

if [ "$#" -ne 3 ] 
  then
    echo "Usage : sh vertexai-apply.sh <gcp project> <region> <run name> <network>"
    exit -1
fi

PROJECT=$1
REGION=$2
RUN_NAME=$3
DEPLOYMENT=deploy$RUN_NAME
ENDPOINT_NAME=endpoint-$RUN_NAME

# retrieve the recently created index id
FULL_INDEX_ENDPOINT_ID=$(gcloud beta ai index-endpoints list --region $REGION --filter="displayName=$ENDPOINT_NAME" --format='value(name)')

if [ -n "${FULL_INDEX_ENDPOINT_ID}" ]; then
    INDEX_ENDPOINT_ID=$(basename $FULL_INDEX_ENDPOINT_ID)
    # undeploy the index from the endpoint
    gcloud ai index-endpoints undeploy-index $INDEX_ENDPOINT_ID --deployed-index-id=$DEPLOYMENT --project=$PROJECT --region=$REGION --quiet || true

    # delete the index endpoint
    gcloud ai index-endpoints delete $INDEX_ENDPOINT_ID --project=$PROJECT --region=$REGION --quiet
fi

