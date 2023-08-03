FROM ubuntu:latest

# get prerequisites 
RUN apt-get update
RUN apt -y install apt-transport-https ca-certificates gnupg curl sudo software-properties-common
# retrieve google cloud sdk keys
RUN echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] http://packages.cloud.google.com/apt cloud-sdk main" | tee -a /etc/apt/sources.list.d/google-cloud-sdk.list && curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key --keyring /usr/share/keyrings/cloud.google.gpg  add - 
# same for terraform and install the repo
RUN curl https://apt.releases.hashicorp.com/gpg | gpg --dearmor > hashicorp.gpg && install -o root -g root -m 644 hashicorp.gpg /etc/apt/trusted.gpg.d/
RUN apt-add-repository "deb [arch=$(dpkg --print-architecture)] https://apt.releases.hashicorp.com $(lsb_release -cs) main"
RUN apt-get update
# install jdk 17, python 11, terraform, maven and gcloud
RUN apt -y install openjdk-17-jdk maven python3.11 google-cloud-cli terraform
# setup python 3.11 as the default python versions
RUN update-alternatives --install /usr/bin/python python /usr/bin/python3.11 20
RUN update-alternatives --install /usr/bin/python3 python3 /usr/bin/python3.11 20

RUN mkdir -p /tmp/source

WORKDIR /tmp/source

# copy the source files to the container
COPY ../*.sh /tmp/source/
COPY ../pom.xml /tmp/source/
COPY ../infra /tmp/source/
COPY ../content-extraction /tmp/source/
COPY ../python-embeddings /tmp/source/
COPY ../service-clients /tmp/source/
COPY ../services /tmp/source/
