#!/bin/bash
set -xeu

if [ "$#" -ne 2 ] 
  then
    echo "Usage : sh retrieve-indexendpoint-id.sh <region> <run name>"
    exit -1
fi

REGION=$1
NAME=$2

INDEX_ENDPOINT_ID=$(gcloud alpha ai index-endpoints list --region $REGION --filter="displayName=endpoint-$NAME" --format='value(name)' --quiet)

jq -n --arg index_endpoint_id "$INDEX_ENDPOINT_ID" '{"index_endpoint_id":$index_endpoint_id}'
