#!/bin/bash

if [ "$#" -ne 1 ]
  then
    echo "Usage : sh tf-outputs.sh <run name>"
    exit -1
fi

NAME=$1

# capture the outputs in variables
TF_JSON_OUTPUT=$(terraform output -json)
DF_SA=$(echo $TF_JSON_OUTPUT | jq .df_sa.value | tr -d '"')
DF_SA_ID=$(echo $TF_JSON_OUTPUT | jq .df_sa_id.value | tr -d '"')
INDEX_ID=$(echo $TF_JSON_OUTPUT | jq .index_id.value | tr -d '"')
SUBNET=$(echo $TF_JSON_OUTPUT | jq .subnet.value | tr -d '"')
INDEX_ENDPOINT_DOMAIN=$(echo $TF_JSON_OUTPUT | jq .index_endpoint_domain.value | tr -d '"')
INDEX_ENDPOINT_ID=$(echo $TF_JSON_OUTPUT | jq .index_endpoint_id.value | tr -d '"')
INDEX_ENDPOINT_DEPLOYMENT=deploy$NAME
SECRET_SERVICE_CONFIG=$(echo $TF_JSON_OUTPUT | jq .secret_service_configuration.value | tr -d '"')
EMBEDDINGS_CONFIG=$(echo $TF_JSON_OUTPUT | jq .embeddings_models.value | tr -d '"')
VECTOR_CONFIG=$(echo $TF_JSON_OUTPUT | jq .vector_storages.value | tr -d '"')
CHUNKER_CONFIG=$(echo $TF_JSON_OUTPUT | jq .chunkers.value | tr -d '"')

echo " "
echo "******************************************************************************************************************************************************"
echo " "
echo "Now that the infrastructure and service accounts have been created, and in case of needing to access cross-organization documents, "
echo "please follow the steps to grant domain-wide delegation to the service account."
echo "Follow steps at: https://developers.google.com/workspace/guides/create-credentials#optional_set_up_domain-wide_delegation_for_a_service_account"
echo " service account: $DF_SA"
echo " service account client id: $DF_SA_ID"
echo " scopes to add: https://www.googleapis.com/auth/documents and https://www.googleapis.com/auth/drive"
echo " "
echo "Also, in all cases, remember to enable permissions on the folders or documents for the email $DF_SA"
echo " "
echo "******************************************************************************************************************************************************"
echo " "
