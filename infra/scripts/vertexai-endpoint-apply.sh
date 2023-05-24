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
ENDPOINT_NAME=endpoint-$RUN_NAME

# Format request body
BODY=$(cat <<EOF
{
  "display_name": "$ENDPOINT_NAME",
  "publicEndpointEnabled": "true"
}
EOF
)

curl -X POST \
    -H "Authorization: Bearer $(gcloud auth print-access-token)" \
    -H "Content-Type: application/json; charset=utf-8" \
    -d "$BODY" \
    "https://$REGION-aiplatform.googleapis.com/v1/projects/$PROJECT/locations/$REGION/indexEndpoints"