#!/bin/bash
set -eu


if [ "$#" -ne 3 ] 
  then
    echo "Usage : sh build.sh <gcp project> <run name> <gcp region> " 
    exit -1
fi

PROJECT_ID=$1
RUN_NAME=$2
REGION=$3

echo "compile and install java source"
mvn install -DskipTests

echo "compile and install python transforms"
pushd python-embeddings
source create_container.sh $PROJECT_ID $REGION
pip3 install .
popd

echo "create service container"
pushd services
source create_container.sh $PROJECT_ID $RUN_NAME $REGION
popd
