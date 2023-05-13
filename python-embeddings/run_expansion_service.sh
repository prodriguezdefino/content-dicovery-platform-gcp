#!/bin/bash
set -eu

if [ "$#" -ne 3 ] 
  then
    echo "Usage : sh run_test_pipeline.sh <gcp project> <gcp region> <port>"
    exit -1
fi

PROJECT=$1
REGION=$2
PORT=$3

# checking if a version of it is already running, fail if so
if [ $(pgrep "apache_beam.runners.portability.expansion_service_main") -eq 0 ]; then
    echo "Expansion Service already running..."
    exit 0
fi

python3 -m apache_beam.runners.portability.expansion_service_main \
    --port $PORT \
    --fully_qualified_name_glob "*" \
    --environment_type="DOCKER" \
    --environment_config=gcr.io/$PROJECT/$REGION/beam-embeddings &

EXP_SERVICE_PID=$(echo $!)