#!/bin/bash
set -xeu

if [ "$#" -ne 3 ] 
  then
    echo "Usage : sh run_test_pipeline.sh <gcp project> <gcp region> <port>"
    exit -1
fi

PROJECT=$1
REGION=$2
PORT=$3

python3 -m apache_beam.runners.portability.expansion_service_main \
    --port $PORT \
    --fully_qualified_name_glob "*" \
    --environment_type="DOCKER" \
    --environment_config=gcr.io/$PROJECT/$REGION/beam-embeddings &

EXP_SERVICE_PID=$(echo $!)