/*
 * Copyright (C) 2023 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.pso.beam.contentextract.transforms;

import com.google.cloud.pso.beam.contentextract.ContentExtractionOptions;
import com.google.cloud.pso.beam.contentextract.utils.ServiceClientProvider;
import com.google.cloud.pso.beam.contentextract.utils.Utilities;
import java.util.List;
import java.util.Map;
import org.apache.beam.sdk.coders.DoubleCoder;
import org.apache.beam.sdk.coders.IterableCoder;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.extensions.python.PythonExternalTransform;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.Reshuffle;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PDone;
import org.apache.beam.sdk.values.TypeDescriptors;

/** */
public abstract class FoundationModelsTransform
    extends PTransform<PCollection<KV<String, List<String>>>, PDone> {

  public static ProcessContentForEmbeddings processAndStoreEmbeddings() {
    return new ProcessContentForEmbeddings();
  }

  public static class ProcessContentForEmbeddings extends FoundationModelsTransform {
    private static final String PYTHON_EMBEDDINGS_TRANSFORM =
        "beam.embeddings.transforms.ExtractEmbeddingsTransform";

    @Override
    @SuppressWarnings("deprecation")
    public PDone expand(PCollection<KV<String, List<String>>> input) {
      var options = input.getPipeline().getOptions().as(ContentExtractionOptions.class);
      input
          .apply(
              "PrepContent",
              MapElements.into(
                      TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptors.strings()))
                  .via(Utilities::contentToKeyedParagraphs))
          .apply(
              "GenerateEmbeddings",
              PythonExternalTransform
                  .<PCollection<KV<String, String>>,
                      PCollection<KV<String, Iterable<Iterable<Double>>>>>
                      from(PYTHON_EMBEDDINGS_TRANSFORM, options.getExpansionService())
                  .withKwargs(
                      Map.of(
                          "project",
                          options.getProject(),
                          "staging_bucket",
                          options.getTempLocation()))
                  .withOutputCoder(
                      KvCoder.of(
                          StringUtf8Coder.of(),
                          IterableCoder.of(IterableCoder.of(DoubleCoder.of())))))
          .apply("ConsolidateEmbeddings", Reshuffle.viaRandomKey())
          .apply(
              "FormatEmbeddings",
              MapElements.into(
                      TypeDescriptors.lists(
                          TypeDescriptors.kvs(
                              TypeDescriptors.strings(),
                              TypeDescriptors.lists(TypeDescriptors.doubles()))))
                  .via(Utilities::embeddingToRightTypes))
          .apply(
              "UpsertIndexDatapoints",
              ParDo.of(
                  new MatchingEngineDatapointUpsertDoFn(
                      ServiceClientProvider.create(
                          options.getRegion(),
                          options.getSecretManagerId(),
                          options.getMatchingEngineIndexId()))));

      return PDone.in(input.getPipeline());
    }

    static class MatchingEngineDatapointUpsertDoFn
        extends DoFn<List<KV<String, List<Double>>>, Void> {

      private final ServiceClientProvider serviceClient;

      public MatchingEngineDatapointUpsertDoFn(ServiceClientProvider serviceClient) {
        this.serviceClient = serviceClient;
      }

      @ProcessElement
      public void process(ProcessContext context) {
        serviceClient.upsertVectorDBDataPoints(context.element());
      }
    }
  }
}
