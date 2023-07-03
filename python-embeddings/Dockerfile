FROM apache/beam_python3.11_sdk:2.48.0

# just in case lets upgrade pip 
RUN pip install --upgrade pip
# pre install known dependencies
RUN pip install 'apache_beam[gcp]==2.48.0' 'google-cloud-aiplatform==1.25' 'pandas'
# copy the local setup into the container
COPY . /tmp/install/
# install the manually downloaded dependencies
RUN for x in `ls /tmp/install/libs/*.whl`; do pip install /tmp/install/libs/$x; done
# install embeddings module on the container
RUN pip install /tmp/install/
# clean up 
RUN rm -rf /tmp/install
