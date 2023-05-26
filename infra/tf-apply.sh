#!/bin/bash
set -eu

if [ "$#" -ne 3 ] 
  then
    echo "Usage : sh tf-apply.sh <gcp project> <region> <run name>"
    exit -1
fi

NAME=$3
REGION=$2
PROJECT=$1

terraform init && terraform apply \
  -var="run_name=${NAME}"         \
  -var="project=${PROJECT}"       \
  -var="region=${REGION}"         
