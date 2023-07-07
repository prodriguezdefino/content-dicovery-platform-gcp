#!/bin/bash
set -eu

if [ "$#" -ne 4 ] 
  then
    echo "Usage : sh deploy_infra.sh <gcp project> <state-bucket-name> <a run name> <region>" 
    exit -1
fi

PROJECT_ID=$1
STATE_BUCKET=$2
RUN_NAME=$3
REGION=$4

echo " "
echo "********************************************"
echo "Creating GCP infrastructure"
echo "********************************************"
echo " "

pushd infra

# we need to create infrastructure, service account and some of the permissions
source ./tf-apply.sh $PROJECT_ID $STATE_BUCKET $REGION $RUN_NAME 

popd