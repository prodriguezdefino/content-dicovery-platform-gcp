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
REGION=us-central1

echo "creating infrastructure"
pushd infra

# we need to create infrastructure, service account and some of the permissions
source ./tf-apply.sh $PROJECT_ID $REGION $RUN_NAME 
# $DF_SA variable was init in the infra setup script

popd

# build and push the image for the python container that extracts embeddings 
echo "bootstrap the local expansion service for Beam multi-lang pipeline"
pushd python-embeddings
source create_container.sh $PROJECT $REGION
pip3 install .
# capture a random open port
PORT=$(python3 -c 'import socket; s=socket.socket(); s.bind(("", 0)); print(s.getsockname()[1]); s.close()')
# start expansion service with the custom python image
source run_expansion_service.sh $PROJECT $REGION $PORT 
# EXP_SERVICE_PID is captured in the previous script
echo "expansion service pid: $EXP_SERVICE_PID"
popd

echo "starting main pipeline"

PIPELINE_NAME=ContentExtractionPipeline

BUCKET=gs://${RUN_NAME}-content-${PROJECT_ID}
JOBNAME=doc-content-extraction-`echo "$RUN_NAME" | tr _ -`-${USER}

LAUNCH_PARAMS=" \
 --project=${PROJECT_ID} \
 --jobName=$JOBNAME \
 --runner=DataflowRunner \
 --region=$REGION \
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
 --secretId=$RUN_NAME \
 --experiments=use_runner_v2 \
 --sdkHarnessContainerImageOverrides=".*python.*,gcr.io/$PROJECT_ID/$REGION/beam-embeddings" \
 --expansionService=localhost:$PORT \
 --usePublicIps=false "

if (( $# == 3 ))
then
  LAUNCH_PARAMS=$LAUNCH_PARAMS$3
fi

mvn compile exec:java -Dexec.mainClass=com.google.cloud.pso.beam.contentextract.${PIPELINE_NAME} -Dexec.cleanupDaemonThreads=false -Dexec.args="${LAUNCH_PARAMS}"
