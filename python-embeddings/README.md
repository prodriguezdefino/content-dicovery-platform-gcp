# Foundation Models Beam Python Module

This folder contains the Apache Beam types needed to expose a PTransform that can request multiple functionalities to a GCP Vertex AI Foundation Model.

As an example the `run_test_pipeline.sh` script demonstrate how to use this module to extract Embeddings from an arbitrary string based content. Check on the `src/beam/embeddings/testpipeline.py` for an Apache Beam Python pipeline example.

## Local installation

To locally install the module just run `pip install .` in this folder.

## Docker Container Setup

The `create_container.sh` script will take care of creating a Docker based container and upload it to the configured project's GCR. This script depends on a local Docker, or other compatible software, locally installed in the machine.

## Cross Language interaction

This module can also be used on Java based pipelines, to aid on this the `run_expansion_service.sh` script takes care of launching a local expansion service instance that will be used to handle the needed wiring on Beam framework exposing the functionality implemented on this module. For a full example on how to use it, review the script `start.sh` on the root folder of this repository.