#!/bin/bash
set -eu


if [ "$#" -ne 2 ] && [ "$#" -ne 3 ]
  then
    echo "Usage : sh execute-suite-example.sh <gcp project> <a run name> <optional params>" 
    exit -1
fi

MORE_PARAMS=""

if (( $# == 3 ))
then
  MORE_PARAMS=$MORE_PARAMS$3
fi

PROJECT_ID=$1
RUN_NAME=$2

echo "creating infrastructure"
pushd infra

# we need to create infrastructure, service account and some of the permissions
source ./tf-apply.sh $PROJECT_ID $RUN_NAME 

popd

# $DF_SA variable was init in the infra setup script
PIPELINE_NAME=ContentExtractionPipeline

BUCKET=gs://${RUN_NAME}-content-${PROJECT_ID}

LAUNCH_PARAMS=" \
 --project=${PROJECT_ID} \
 --runner=DataflowRunner \
 --region=us-central1 \
 --streaming \
 --stagingLocation=$BUCKET/dataflow/staging \
 --tempLocation=$BUCKET/dataflow/temp \
 --gcpTempLocation=$BUCKET/dataflow/gcptemp \
 --maxNumWorkers=10 \
 --numWorkers=1 \
 --subscription=projects/$PROJECT_ID/subscriptions/$RUN_NAME-sub \
 --bucketLocation=$BUCKET/content \
 --experiments=min_num_workers=1 \
 --workerMachineType=n2d-standard-4 \
 --autoscalingAlgorithm=THROUGHPUT_BASED \
 --enableStreamingEngine \
 --serviceAccount=$DF_SA \
 --usePublicIps=false "

if (( $# == 3 ))
then
  LAUNCH_PARAMS=$LAUNCH_PARAMS$3
fi

mvn compile exec:java -Dexec.mainClass=com.google.cloud.pso.beam.contentextract.${PIPELINE_NAME} -Dexec.cleanupDaemonThreads=false -Dexec.args="${LAUNCH_PARAMS}"
