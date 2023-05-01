#!/bin/bash
set -eu

if [ "$#" -ne 2 ] 
  then
    echo "Usage : sh tf-apply.sh <gcp project> <run name>"
    exit -1
fi

NAME=$2
PROJECT=$1

terraform destroy \
  -var="run_name=${NAME}"           \
  -var="project=${PROJECT}"            
