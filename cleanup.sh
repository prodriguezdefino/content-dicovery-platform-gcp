#!/bin/bash
set -eu

if [ "$#" -ne 3 ]
  then
    echo "Usage : sh cleanup.sh <gcp project> <state-bucket-name> <run name>" 
    exit -1
fi

GCP_PROJECT=$1
STATE_BUCKET=$2
RUN_NAME=$3
REGION=us-central1

function drain_job(){
  JOB_NAME=$1
  REGION=$2
  # get job id 
  JOB_ID=$(gcloud dataflow jobs list --filter="name=${JOB_NAME}" --status=active --format="value(JOB_ID)" --region=${REGION})
  # drain job
  if [ ! -z "$JOB_ID" ] 
  then 
    gcloud dataflow jobs drain $JOB_ID --region=${REGION}
    STATUS=""
    while [[ $STATUS != "JOB_STATE_DRAINED" ]]; 
    do
      echo "draining..." 
      sleep 30
      STATUS=$(gcloud dataflow jobs describe ${JOB_ID} --format='value(currentState)' --region=${REGION}) 
    done
  fi
}

echo "draining dataflow job"

JOBNAME=doc-content-extraction-`echo "$RUN_NAME" | tr _ -`-${USER}

drain_job $JOBNAME $REGION

echo "removing infrastructure"
pushd infra

# answering anything but `yes` will keep the infra in place for review
source ./tf-destroy.sh $GCP_PROJECT $STATE_BUCKET $REGION $RUN_NAME 

popd
