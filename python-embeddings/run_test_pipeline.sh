RUN_NAME
PROJECT

python3 -m beam.embeddings.testpipeline \
    --output gs://$RUN_NAME-content-$PROJECT/output \
    --ai_project=$PROJECT \
    --ai_staging_location=gs://$RUN_NAME-content-$PROJECT/ai_staging \
    --performance_runtime_type_check \
    --runner=PortableRunner \
    --job_endpoint=embed \
    --environment_type="DOCKER" \
    --environment_config=gcr.io/$PROJECT/us-central1/beam-embeddings