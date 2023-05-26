#!/bin/bash
set -eu

if [ "$#" -ne 3 ] 
  then
    echo "Usage : sh deploy_infra.sh <gcp project> <a run name> <region>" 
    exit -1
fi

PROJECT_ID=$1
RUN_NAME=$2
REGION=$3

echo " "
echo "********************************************"
echo "Creating GCP infrastructure"
echo "********************************************"
echo " "

pushd infra

# we need to create infrastructure, service account and some of the permissions
source ./tf-apply.sh $PROJECT_ID $REGION $RUN_NAME 

popd