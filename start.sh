#!/bin/bash
set -eu

if [ "$#" -ne 3 ] && [ "$#" -ne 4 ]
  then
    echo "Usage : sh start.sh <gcp project> <state-bucket-name> <a run name> <optional params>"
    exit -1
fi

PROJECT_ID=$1
STATE_BUCKET=$2
RUN_NAME=$3
REGION=us-central1
OTHER=""

if (( $# == 4 ))
then
  OTHER=$4
fi

source build.sh $PROJECT_ID $RUN_NAME $REGION

source deploy_infra.sh $PROJECT_ID $STATE_BUCKET $RUN_NAME $REGION

source deploy_pipeline.sh $PROJECT_ID $RUN_NAME $REGION $OTHER