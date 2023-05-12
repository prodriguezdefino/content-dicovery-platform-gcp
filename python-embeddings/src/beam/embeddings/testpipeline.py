import argparse
import logging

import apache_beam as beam
from apache_beam.io import WriteToText
from apache_beam.options.pipeline_options import PipelineOptions
from beam.embeddings.transforms import ExtractEmbeddingsTransform


def run(argv=None, save_main_session=True):
  class TestOptions(PipelineOptions):

    @classmethod
    def _add_argparse_args(cls, parser):
        parser.add_argument("--output", 
          help="The output location", required=True)
        parser.add_argument("--ai_project", 
          help="AI related GCP Project", required=True)
        parser.add_argument("--ai_staging_location", 
          help="AI related Bucket location", required=True)
  
  parser = argparse.ArgumentParser()
  _, pipeline_args = parser.parse_known_args(argv)

  pipeline_options = PipelineOptions(pipeline_args)
  test_options = pipeline_options.view_as(TestOptions)

  input_text = '''
  This text should be enough to setup an initial input for an embeddings extraction pipeline.
  Probably better results may occur with longer texts, but this is not the goal for this test, 
  the idea is just to try out the pipeline and see that it executes as needed.
  '''
  
  with beam.Pipeline(options=pipeline_options) as p:
    embeddings = (p 
    | 'CreateText'          >> beam.Create([input_text])
    | 'AddKeyToContent'     >> beam.Map(lambda content: ('DocumentID', content))
    | 'GenerateEmbbeddings' >> ExtractEmbeddingsTransform(
        test_options.ai_project, test_options.ai_staging_location)
    | 'WriteToOutput'       >> beam.io.WriteToText(test_options.output))


if __name__ == '__main__':
  logging.getLogger().setLevel(logging.INFO)
  run()