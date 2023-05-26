#!/bin/bash
set -eu


if [ "$#" -ne 2 ] && [ "$#" -ne 3 ]
  then
    echo "Usage : sh start.sh <gcp project> <a run name> <optional params>" 
    exit -1
fi

PROJECT_ID=$1
RUN_NAME=$2
REGION=us-central1
OTHER=""

if (( $# == 3 ))
then
  OTHER=$3
fi

source build.sh $PROJECT_ID $RUN_NAME $REGION

source deploy_infra.sh $PROJECT_ID $RUN_NAME $REGION

source deploy_pipeline.sh $PROJECT_ID $RUN_NAME $OTHER