FROM apache/beam_python3.12_sdk:2.63.0
# just in case lets upgrade pip
RUN pip install --upgrade pip
# pre install known dependencies
RUN pip install 'apache_beam[gcp]==2.63.0' 'google-cloud-aiplatform==1.81.0' 'pandas'
# copy the local setup into the container
COPY . /tmp/install/
# install the manually downloaded dependencies
RUN for x in `ls /tmp/install/libs/*.whl`; do pip install /tmp/install/libs/$x; done
# install embeddings module on the container
RUN pip install /tmp/install/
# clean up
RUN rm -rf /tmp/install