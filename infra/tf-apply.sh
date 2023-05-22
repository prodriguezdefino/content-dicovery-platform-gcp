#!/bin/bash
set -eu

if [ "$#" -ne 3 ] 
  then
    echo "Usage : sh tf-apply.sh <gcp project> <region> <run name>"
    exit -1
fi

NAME=$3
REGION=$2
PROJECT=$1

terraform init && terraform apply \
  -var="run_name=${NAME}"         \
  -var="project=${PROJECT}"       \
  -var="region=${REGION}"         

# capture the outputs in variables
TF_JSON_OUTPUT=$(terraform output -json)
DF_SA=$(echo $TF_JSON_OUTPUT | jq .df_sa.value | tr -d '"')
DF_SA_ID=$(echo $TF_JSON_OUTPUT | jq .df_sa_id.value | tr -d '"')
INDEX_ID=$(echo $TF_JSON_OUTPUT | jq .index_id.value | tr -d '"')
SUBNET=$(echo $TF_JSON_OUTPUT | jq .subnet.value | tr -d '"')

echo " "
echo "******************************************************************************************************************************************************"
echo " "
echo "Now that the infrastructure and service accounts have been created, please follow the steps to grant domain-wide delegation to the service account." 
echo "Follow steps at: https://developers.google.com/workspace/guides/create-credentials#optional_set_up_domain-wide_delegation_for_a_service_account"
echo " service account: $DF_SA"
echo " service account client id: $DF_SA_ID"
echo " scopes to add: https://www.googleapis.com/auth/documents and https://www.googleapis.com/auth/drive"
echo " "
echo "Also, remember to enable permissions on the folders or documents for the email $DF_SA" 
echo " "
echo "******************************************************************************************************************************************************"
echo " "
