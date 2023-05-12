#!/bin/bash
set -xeu

if [ "$#" -ne 2 ] 
  then
    echo "Usage : sh run_test_pipeline.sh <gcp project> <run name>" 
    exit -1
fi

RUN_NAME=$2
PROJECT=$1

python3 -m beam.embeddings.testpipeline \
    --output gs://$RUN_NAME-content-$PROJECT/output \
    --ai_project=$PROJECT \
    --ai_staging_location=gs://$RUN_NAME-content-$PROJECT/ai_staging \
    --runner=PortableRunner \
    --job_endpoint=embed \
    --environment_type="DOCKER" \
    --environment_config=gcr.io/$PROJECT/us-central1/beam-embeddings
    