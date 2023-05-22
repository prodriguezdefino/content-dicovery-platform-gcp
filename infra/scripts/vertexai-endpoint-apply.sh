#!/bin/bash
set -xeu

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

# create the index endpoint
gcloud ai index-endpoints create \
  --display-name="$RUN_NAME-endpoint" \
  --network=$NETWORK \
  --project=$PROJECT \
  --region=$REGION


