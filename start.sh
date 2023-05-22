#!/bin/bash
set -eu


if [ "$#" -ne 2 ] && [ "$#" -ne 3 ]
  then
    echo "Usage : sh execute-suite-example.sh <gcp project> <a run name> <optional params>" 
    exit -1
fi

PROJECT_ID=$1
RUN_NAME=$2
REGION=us-central1

echo " "
echo "********************************************"
echo "Creating GCP infrastructure"
echo "********************************************"
echo " "

pushd infra

# we need to create infrastructure, service account and some of the permissions
source ./tf-apply.sh $PROJECT_ID $REGION $RUN_NAME 
# $DF_SA & $INDEX_ID variables are init in the infra setup script

popd

# build and push the image for the python container that extracts embeddings 
echo " "
echo "********************************************"
echo "Install python mods and deps "
echo "and bootstrap the local expansion service "
echo "for Beam multi-lang pipeline"
echo "********************************************"
echo " "

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

echo " "
echo "********************************************"
echo "Starting Beam pipeline"
echo "********************************************"
echo " "

PIPELINE_NAME=ContentExtractionPipeline

BUCKET=gs://${RUN_NAME}-content-${PROJECT_ID}
JOBNAME=doc-content-extraction-`echo "$RUN_NAME" | tr _ -`-${USER}

LAUNCH_PARAMS=" \
 --project=${PROJECT_ID} \
 --jobName=$JOBNAME \
 --subnetwork=$SUBNET \
 --runner=DataflowRunner \
 --region=$REGION \
 --streaming \
 --stagingLocation=$BUCKET/dataflow/staging \
 --tempLocation=$BUCKET/dataflow/temp \
 --gcpTempLocation=$BUCKET/dataflow/gcptemp \
 --maxNumWorkers=10 \
 --numWorkers=1 \
 --topic=projects/$PROJECT_ID/topics/$RUN_NAME \
 --subscription=projects/$PROJECT_ID/subscriptions/$RUN_NAME-sub \
 --bucketLocation=$BUCKET \
 --matchingEngineIndexId=$INDEX_ID \
 --experiments=min_num_workers=1 \
 --workerMachineType=n2d-standard-4 \
 --autoscalingAlgorithm=THROUGHPUT_BASED \
 --enableStreamingEngine \
 --serviceAccount=$DF_SA \
 --secretManagerId=projects/$PROJECT_ID/secrets/$RUN_NAME/versions/latest \
 --experiments=use_runner_v2 \
 --sdkHarnessContainerImageOverrides=".*python.*,gcr.io/$PROJECT_ID/$REGION/beam-embeddings" \
 --expansionService=localhost:$PORT \
 --usePublicIps=false "

if (( $# == 3 ))
then
  LAUNCH_PARAMS=$LAUNCH_PARAMS$3
fi

mvn compile exec:java -Dexec.mainClass=com.google.cloud.pso.beam.contentextract.${PIPELINE_NAME} -Dexec.cleanupDaemonThreads=false -Dexec.args="${LAUNCH_PARAMS}"
