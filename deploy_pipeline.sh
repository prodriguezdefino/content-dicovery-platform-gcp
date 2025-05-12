#!/bin/bash
set -eu

if [ "$#" -ne 3 ] && [ "$#" -ne 4 ]
  then
    echo "Usage : sh deploy_pipeline.sh <gcp project> <a run name> <region> <optional params>"
    exit -1
fi

PROJECT_ID=$1
RUN_NAME=$2
REGION=$3


pushd infra

source tf-outputs.sh $RUN_NAME

popd

pushd content-extraction
echo " "
echo "********************************************"
echo "Starting Beam pipeline"
echo "********************************************"
echo " "

PIPELINE_NAME=ContentExtractionPipeline

BUCKET=gs://${RUN_NAME}-content-${PROJECT_ID}
JOBNAME=doc-content-extraction-`echo "$RUN_NAME" | tr _ -`-${USER}

LAUNCH_PARAMS=" \
 --project=$PROJECT_ID \
 --jobName=$JOBNAME \
 --subnetwork=$SUBNET \
 --runner=DataflowRunner \
 --region=$REGION \
 --autoscalingAlgorithm=THROUGHPUT_BASED \
 --enableStreamingEngine \
 --streaming \
 --stagingLocation=$BUCKET/dataflow/staging \
 --tempLocation=$BUCKET/dataflow/temp \
 --gcpTempLocation=$BUCKET/dataflow/gcptemp \
 --experiments=min_num_workers=1 \
 --workerMachineType=n2d-standard-2 \
 --maxNumWorkers=10 \
 --numWorkers=1 \
 --experiments=use_runner_v2 \
 --topic=projects/$PROJECT_ID/topics/$RUN_NAME \
 --subscription=projects/$PROJECT_ID/subscriptions/$RUN_NAME-sub \
 --bucketLocation=$BUCKET \
 --matchingEngineIndexId=$INDEX_ID \
 --matchingEngineIndexEndpointId=$INDEX_ENDPOINT_ID \
 --matchingEngineIndexEndpointDomain=$INDEX_ENDPOINT_DOMAIN \
 --matchingEngineIndexEndpointDeploymentName=$INDEX_ENDPOINT_DEPLOYMENT \
 --bigTableInstanceName=$RUN_NAME-instance \
 --embeddingsConfiguration=$EMBEDDINGS_CONFIG \
 --vectorConfiguration=$VECTOR_CONFIG \
 --chunkerConfiguration=$CHUNKER_CONFIG \
 --usePublicIps=false "

if (( $# == 4 ))
then
  LAUNCH_PARAMS=$LAUNCH_PARAMS$4
fi

mvn compile exec:java -Dexec.mainClass=com.google.cloud.pso.beam.contentextract.${PIPELINE_NAME} -Dexec.cleanupDaemonThreads=false -Dexec.args="${LAUNCH_PARAMS}"

popd