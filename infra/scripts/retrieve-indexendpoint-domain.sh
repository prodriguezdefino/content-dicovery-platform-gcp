#!/bin/bash
set -xeu

if [ "$#" -ne 2 ] 
  then
    echo "Usage : sh retrieve-indexendpoint-domain.sh <region> <run name>"
    exit -1
fi

REGION=$1
NAME=$2

INDEX_ENDPOINT_DOMAIN=$(gcloud alpha ai index-endpoints list --region $REGION --filter="displayName=endpoint-$NAME" --format='value(publicEndpointDomainName)' --quiet)

jq -n --arg index_endpoint_domain "$INDEX_ENDPOINT_DOMAIN" '{"index_endpoint_domain":$index_endpoint_domain}'