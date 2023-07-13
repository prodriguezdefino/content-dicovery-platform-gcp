#!/bin/bash
set -eu

if [ "$#" -ne 4 ] 
  then
    echo "Usage : sh tf-destroy.sh <gcp project> <state bucket> <region> <run name>"
    exit -1
fi

NAME=$4
REGION=$3
STATE_BUCKET=$2
PROJECT=$1

terraform init \
 -backend-config="bucket=$STATE_BUCKET" \
 -backend-config="prefix=terraform/state/$NAME" \
 && terraform destroy     \
  -var="run_name=${NAME}"             \
  -var="project=${PROJECT}"           \
  -var="region=${REGION}"                 
