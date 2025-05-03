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

echo " "
echo "********************************************"
echo "Build source code"
echo "********************************************"
echo " "
echo " "
echo " "

echo "compile and install java source"
mvn install -DskipTests
echo " "
echo " "
echo " "
