#!/bin/bash
set -xeu

if [ "$#" -ne 7 ] 
  then
    echo "Usage : sh vertexai-apply.sh <gcp project> <region> <run name> <network> <index id> <min replica count> <max replica count>"
    exit -1
fi

PROJECT=$1
REGION=$2
RUN_NAME=$3
NETWORK=$4
INDEX_ID=$5
MIN_REPLICA=$6
MAX_REPLICA=$7
ENDPOINT_NAME=endpoint-$RUN_NAME
DEPLOYMENT=deploy$RUN_NAME

# retrieve the recently created index id
INDEX_ENDPOINT_ID=$(basename $(gcloud beta ai index-endpoints list --region $REGION --filter="displayName=$ENDPOINT_NAME" --format='value(name)'))
# create the index deployment on the endpoint
gcloud ai index-endpoints deploy-index $INDEX_ENDPOINT_ID \
  --deployed-index-id=$DEPLOYMENT \
  --display-name=$DEPLOYMENT \
  --index=$INDEX_ID \
  --min-replica-count=$MIN_REPLICA \
  --max-replica-count=$MAX_REPLICA \
  --project=$PROJECT \
  --region=$REGION

