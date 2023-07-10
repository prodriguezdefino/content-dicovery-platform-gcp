# Content Discovery Platform - GenAI

This repository contains the code and automation needed to build a simple content discovery platform powered by VertexAI fundational models. This platform should be capable of capturing documents content (initially Google Docs), and with that content generate embeddings vectors to be stored in a vector database powered by VertexAI Matching Engine, later this embeddings can be utilized to contextualize an external consumer general question and with that context request an answer to a VertexAI fundational model to get an answer. 

## Platform Components

The platform can be separated in 4 main components, access service layer, content capture pipeline, content storage and LLMs. The services layer enable external consumers to send document ingestion requests and later on send inquiries about the content included in the previously ingested documents. The content capture pipeline is in charge of capturing the document's content in NRT, extract embeddings and map those embedding with real content that can later on be used to contextualize the external users questions to a LLM. The content storage is separated in 3 differnt purposes, LLM fine tuning, online embeddings matching and chunked content, each of them handled by a specialized storage system and with the general pupose of storing the information needed by the platform's components to implement the ingestion and query uses cases. Last but not least the platform makes use of 2 specialized LLMs to create real time embeddings from the ingestied document content and another one in charge of generating the answers requested by the platform's users.

## GCP Technologies

All the componente s described before are implemented using publicly available GCP services. To enumerate them: Cloud Build, Cloud Run, Cloud Dataflow, Cloud Pubsub, Cloud Storage, Cloud Bigtable, Vertex AI Matching Engine, Vertex AI Fundational models (embeddings and text-bison), alongside with Google Docs and Google Drive as the content infromation sources.

## Architecture

The next image shows how the different components of the architecture and technologies interact betweeen each other.

![Platform's diagram](images/diagram.png)

## Deployment Automation

This platform uses Terraform for the setup of all its components. For those that currently does not have native support we have created null_resource wrappers, this are good workarounds but they tend to have very rough edges so be aware of potential errors.

The complete deployment as of today (June 2023) can take up to 90 mintues to complete, the biggest culprit being the Matching Engine related components that take the majority of that time to be created and readily available. With time this extended runtimes will only improve. 

## First setup 

The setup should be executable from the scripts included in the repository. 

### Requirements

There are a few requirements needed to be fulfilled to deploy this platform being those: 

* A gcloud installation
* A services account or user configured in gcloud with enough permissions to create the needed resources 
* A python 3.11 installation
* A Java 17 installation
* A Terraform 0.12+ installation

### Triggering a deployment 

In order to have all the components deployed in GCP we need to build, create infrastructure and later on deploy the services and pipelines. 

To achieve this we included the script `start.sh` which basically orchestrate the other included scripts to accomplish the full deployment goal. 

Also we have included a `cleanup.sh` script in charge of destroying the infrastructure and clean up the collected data. 

### Google Docs Permissions

Once the infrastructure is setup, the deployment process will print out instructions to grant the service account that runs the content extraction pipeline broad permissions to impersonate Google Workspace document access. This is not an ideal setup, but for PoC purposes should be sufficient. Once those permissions are granted, in order to extract the actual content, the service account should have permissions to the documents and Google Drive folders provided as URLs.

## Exposed Services

The solution exposes a couple of resources through GCP CloudRun, which can be used to interact for content ingestion and content discovery queries. In all the examples we use the symbolic `<service-address>` string, which should be replaced by the URL provided by CloudRun after the service deployment is completed.

### Content Ingestion

This service is capable of ingesting data from documents hosted in Google Drive or self contained multi-part requests which contain a document identifier and the document's content encoded as binary. 

#### Google Drive Content Ingestion

The Google Drive ingestion is done by sending a HTTP request simielar to the next example 
```bash
$ > curl -X POST -H "Content-Type: application/json" \
    -H "Authorization: Bearer $(gcloud auth print-identity-token)" \
    https://<service-address>/ingest/content/gdrive \
    -d $'{"url":"https://docs.google.com/document/d/somevalid-googledocid"}'
```
This request will indicate the platform to grab the document from the provided `url` and in case the service account that runs the ingestion has access permissions to the document, it will extract the content from it and store the information for indexing, later discovery and retrieval.

The request can contain the url of a Google document or a Google Drive folder, in the last case the ingestion will crawl the folder for documents to process. Also, is possible to use the property `urls` which expect a `JSONArray` of `string` values, each of them a valid Google Document url.

#### Multipart Content Ingestion

In the case of wanting to include the content of an article, document, or page that is locally accessible by the ingest client, using the multipart endpoint should be sufficient to ingest the document. See the next `curl` command as an example, the service expects that the `documentId` form field is set to identify and univocally index the content:
```bash 
$ > curl -H "Authorization: Bearer $(gcloud auth print-identity-token)" \
  -F documentId=<somedocid> \
  -F documentContent=@</some/local/directory/file/to/upload> \
  https://<service-address>/ingest/content/multipart
```

### Querying for Content

This service exposes the query capability to the platform's users, by sending natural text queries to the services and given there is already content indexes after ingestion in the platform, the service will come back with information summarized through by the LLM model. 

#### Example Query Requests

The interaction with the service can be done through a REST exchange, similar to those for the ingestion part, as seen in the next example. 

```bash
$ > curl -X POST \
 -H "Content-Type: application/json" \
 -H "Authorization: Bearer $(gcloud auth print-identity-token)" \
 https://<service-address>/query/content \
 -d $'{"text":"summarize the benefits of using VertexAI foundational models for Generative AI applications?", "sessionId": ""}' \
 | jq .

# response from service
{
  "content": "VertexAI foundational models are a set of pre-trained models that can be used to build and deploy machine learning applications. They are available in a variety of languages and frameworks, and can be used for a variety of tasks, including natural language processing, computer vision, and recommendation systems.\n\nVertexAI foundational models are a good choice for Generative AI applications because they provide a starting point for building these types of applications. They can be used to quickly and easily create models that can generate text, images, and other types of content.\n\nIn addition, VertexAI foundational models are scalable and can be used to process large amounts of data. They are also reliable and can be used to create applications that are available 24/7.\n\nOverall, VertexAI foundational models are a powerful tool for building Generative AI applications. They provide a starting point for building these types of applications, and they can be used to quickly and easily create models that can generate text, images, and other types of content.",
  "sourceLinks": [
  ]
}
```

##### Note:
There is an special case here, where there is no information stored yet for a particular topic, if that topic falls into the GCP landscape then the model will be acting as an expert since we [setup a prompt](https://github.com/prodriguezdefino/content-dicovery-platform-gcp/blob/main/services/src/main/java/com/google/cloud/pso/data/services/PromptUtilities.java#L33) that indicates that to the model request. 

#### Contextful Exchanges (conversations)

In case of wanting to have a more context-aware type of exchange with the service, a session identifier (`sessionId` property in the JSON request) should be provided for the service to use as a conversation exchange key. This conversation key will be used to setup the right context to the model (by summarizing previous exchanges) and keeping track of the last 5 exchanges (at least). Also worth to note that the exchange history will be maitained for 24hrs, this can be changed as part of the gc policies of the BigTable storage in the platform. 

Next an example for a context-aware conversation: 
```bash
$ > curl -X POST \
 -H "Content-Type: application/json" \
 -H "Authorization: Bearer $(gcloud auth print-identity-token)" \
 https://<service-address>/query/content \
 -d $'{"text":"summarize the benefits of using VertexAI foundational models for Generative AI applications?", "sessionId": "some-session-id"}' \
 | jq .

# response from service
{
  "content": "VertexAI Foundational Models are a suite of pre-trained models that can be used to accelerate the development of Generative AI applications. These models are available in a variety of languages and domains, and they can be used to generate text, images, audio, and other types of content.\n\nUsing VertexAI Foundational Models can help you to:\n\n* Reduce the time and effort required to develop Generative AI applications\n* Improve the accuracy and quality of your models\n* Access the latest research and development in Generative AI\n\nVertexAI Foundational Models are a powerful tool for developers who want to create innovative and engaging Generative AI applications.",
  "sourceLinks": [
  ]
}

$ > curl -X POST \
 -H "Content-Type: application/json" \
 -H "Authorization: Bearer $(gcloud auth print-identity-token)" \
 https://<service-address>/query/content \
 -d $'{"text":"describe the available LLM models?", "sessionId": "some-session-id"}' \
 | jq .

# response from service
{
  "content": "The VertexAI Foundational Models suite includes a variety of LLM models, including:\n\n* Text-to-text LLMs: These models can generate text based on a given prompt. They can be used for tasks such as summarization, translation, and question answering.\n* Image-to-text LLMs: These models can generate text based on an image. They can be used for tasks such as image captioning and description generation.\n* Audio-to-text LLMs: These models can generate text based on an audio clip. They can be used for tasks such as speech recognition and transcription.\n\nThese models are available in a variety of languages, including English, Spanish, French, German, and Japanese. They can be used to create a wide range of Generative AI applications, such as chatbots, customer service applications, and creative writing tools.",
  "sourceLinks": [
  ]
}

$ > curl -X POST \
 -H "Content-Type: application/json" \
 -H "Authorization: Bearer $(gcloud auth print-identity-token)" \
 https://<service-address>/query/content \
 -d $'{"text":"do rate limit apply for those LLMs?", "sessionId": "some-session-id"}' \
 | jq .

# response from service
{
  "content": "Yes, there are rate limits for the VertexAI Foundational Models. The rate limits are based on the number of requests per second and the total number of requests per day. For more information, please see the [VertexAI Foundational Models documentation](https://cloud.google.com/vertex-ai/docs/foundational-models#rate-limits).",
  "sourceLinks": [
  ]
}

$ > curl -X POST \
 -H "Content-Type: application/json" \
 -H "Authorization: Bearer $(gcloud auth print-identity-token)" \
 https://<service-address>/query/content \
 -d $'{"text":"care to share the price?", "sessionId": "some-session-id"}' \
 | jq .

# response from service
{
  "content": "The VertexAI Foundational Models are priced based on the number of requests per second and the total number of requests per day. For more information, please see the [VertexAI Foundational Models pricing page](https://cloud.google.com/vertex-ai/pricing#foundational-models).",
  "sourceLinks": [
  ]
}
```
