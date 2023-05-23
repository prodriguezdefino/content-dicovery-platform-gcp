from google.cloud import aiplatform
from collections import deque
from vertexai.preview.language_models import TextEmbeddingModel
from typing import Tuple

import logging
import pandas as pd
import numpy as np
import apache_beam as beam 

class ExtractEmbeddingsTransform(beam.PTransform):
    def __init__(self, project, staging_bucket, region='us-central1'):
        self.project = project
        self.staging_bucket = staging_bucket
        self.region = region
        self.init_chunk_size=500
        self.split_chunk_group_size=3
        self.chunk_overlap=1

    def expand(self, pcoll):
        # Transform logic goes here.
        return (pcoll 
            | 'ExtractEmbeddings' >> beam.ParDo(
                ExtractEmbeddingsDoFn(
                    self.project,
                    self.staging_bucket,
                    self.region,
                    self.init_chunk_size, 
                    self.split_chunk_group_size, 
                    self.chunk_overlap)))


    def with_init_chunk_size(self, size:int):
        self.init_chunk_size = size
    

    def with_split_chunk_group_size(self, size:int):
        self.split_chunk_group_size = size


    def with_chunk_overlap(self, overlap:int):
        self.chunk_overlap = overlap


class ExtractEmbeddingsDoFn(beam.DoFn):
    def __init__(self, 
            project, staging_bucket, region, init_chunk_size=500,
            split_chunk_group_size=3, chunk_overlap=1):
        self.embedded_model = None 
        self.init_chunk_size=init_chunk_size
        self.split_chunk_group_size=split_chunk_group_size
        self.chunk_overlap=chunk_overlap
        self.gcp_project = project
        self.staging_bucket = staging_bucket
        self.region = region


    def setup(self):
        aiplatform.init(
            project=self.gcp_project,
            location=self.region,
            staging_bucket=self.staging_bucket)
        self.embedded_model = TextEmbeddingModel.from_pretrained("textembedding-gecko")


    def get_embeddings(self, row):
        emb = self.embedded_model.get_embeddings([row])
        return [each.values for each in emb][0]


    def get_chunks_iter(self, s, maxlength):
        start = 0
        end = 0
        while start + maxlength  < len(s) and end != -1:
            end = s.rfind(" ", start, start + maxlength + 1)
            yield s[start:end]
            start = end +1
        yield s[start:]


    def overlapping_chunks(self, iterable, chunk_size=3, overlap=1):
        # we'll use a deque to hold the values because it automatically
        # discards any extraneous elements if it grows too large
        if chunk_size < 1:
            raise Exception("chunk size too small")
        if overlap >= chunk_size:
            raise Exception("overlap too large")
        queue = deque(maxlen=chunk_size)
        it = iter(iterable)
        i = 0
        try:
            # start by filling the queue with the first group
            for i in range(chunk_size):
                queue.append(next(it))
            while True:
                yield tuple(queue)
                # after yielding a chunk, get enough elements for the next chunk
                for i in range(chunk_size - overlap):
                    queue.append(next(it))
        except StopIteration:
            # if the iterator is exhausted, yield any remaining elements
            i += overlap
            if i > 0:
                yield tuple(queue)[-i:]


    def get_data_embeddings(self, content, init_chunk_size=500,
               split_chunk_group_size=3, chunk_overlap=1):
        docId, input_text = content
        chunks_iter = self.get_chunks_iter(input_text, init_chunk_size)
        chunks = self.overlapping_chunks(
            chunks_iter, chunk_size=split_chunk_group_size, overlap=chunk_overlap)
        chunks_final = ["\n".join(n) for n in chunks]
        embeddings = []
        for chunk in chunks_final:
            emb = self.get_embeddings(chunk)
            embeddings.append((docId, emb))
        return embeddings


    def process(self, content: Tuple[str, str]):
        yield self.get_data_embeddings(
            content, 
            self.init_chunk_size, 
            self.split_chunk_group_size, 
            self.chunk_overlap)
