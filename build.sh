#!/bin/bash
set -eu


if [ "$#" -ne 2 ] 
  then
    echo "Usage : sh build.sh <gcp project> <gcp region> " 
    exit -1
fi

PROJECT_ID=$1
REGION=$2

echo "compile java pipeline"
mvn clean package -DskipTests

echo "compile and install python transforms"
pushd python-embeddings
source create_container.sh $PROJECT $REGION
pip3 install .
popd
