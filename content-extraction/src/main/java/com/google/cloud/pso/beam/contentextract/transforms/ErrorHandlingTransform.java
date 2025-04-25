/*
 * Copyright (C) 2025 Google Inc.
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
import com.google.cloud.pso.beam.contentextract.Types;
import com.google.cloud.pso.beam.contentextract.Types.*;
import java.util.stream.StreamSupport;
import org.apache.beam.repackaged.core.org.apache.commons.lang3.RandomStringUtils;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.SerializableCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.FileIO;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.WriteFilesResult;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.transforms.Contextful;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.FlatMapElements;
import org.apache.beam.sdk.transforms.Flatten;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.transforms.WithFailures;
import org.apache.beam.sdk.transforms.WithKeys;
import org.apache.beam.sdk.transforms.windowing.AfterWatermark;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Repeatedly;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollectionList;
import org.apache.beam.sdk.values.TupleTag;
import org.apache.beam.sdk.values.TupleTagList;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.beam.sdk.values.TypeDescriptors;
import org.joda.time.Duration;

/** */
public class ErrorHandlingTransform
    extends PTransform<PCollectionList<ProcessingError>, WriteFilesResult<String>> {

  private static final Integer MAX_RETRIES = 100;
  public static final TupleTag<Retriable> TO_RETRY = new TupleTag<>() {};
  public static final TupleTag<Discardable> TO_DISCARD = new TupleTag<>() {};

  ErrorHandlingTransform() {}

  public static ErrorHandlingTransform create() {
    return new ErrorHandlingTransform();
  }

  @Override
  public WriteFilesResult<String> expand(PCollectionList<ProcessingError> input) {
    var options = input.getPipeline().getOptions().as(ContentExtractionOptions.class);

    var maybeRetry =
        input
            .apply("FlattenErrors", Flatten.pCollections())
            .apply(
                "DecideIfRetry",
                ParDo.of(new DecideErrorDoFn())
                    .withOutputTags(TO_RETRY, TupleTagList.of(TO_DISCARD)));

    maybeRetry
        .get(TO_RETRY)
        .apply(
            "Apply10mWindow",
            Window.<Retriable>into(FixedWindows.of(Duration.standardMinutes(10)))
                .triggering(Repeatedly.forever(AfterWatermark.pastEndOfWindow()))
                .discardingFiredPanes())
        .apply(
            "AddErrorKey",
            WithKeys.<String, Retriable>of(retriable -> RandomStringUtils.randomAlphabetic(5)))
        .setCoder(KvCoder.of(StringUtf8Coder.of(), SerializableCoder.of(Retriable.class)))
        .apply("GroupByErrorKey", GroupByKey.create())
        .apply(
            "FormatAsPubsubMessage",
            FlatMapElements.into(TypeDescriptor.of(PubsubMessage.class))
                .via(
                    retries ->
                        StreamSupport.stream(retries.getValue().spliterator(), false)
                            .map(Retriable::toPubsubMessage)
                            .toList()))
        .apply("SendToPubsub", PubsubIO.writeMessages().to(options.getTopic()));

    return maybeRetry
        .get(TO_DISCARD)
        .apply(
            "FormatAsError",
            MapElements.into(
                    TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptors.strings()))
                .via(discardable -> KV.of(discardable.element(), discardable.toErrorString())))
        .apply(
            "WriteErrorsToGCS",
            FileIO.<String, KV<String, String>>writeDynamic()
                .by(error -> error.getKey().replaceAll("[\\n\\r]", ""))
                .withDestinationCoder(StringUtf8Coder.of())
                .via(Contextful.fn(error -> error.getValue()), TextIO.sink())
                .to(options.getBucketLocation())
                .withNaming(
                    Contextful.fn(
                        contentName ->
                            FileIO.Write.defaultNaming("errors/" + contentName, ".txt"))));
  }

  static class DecideErrorDoFn extends DoFn<ProcessingError, Retriable> {

    @ProcessElement
    public void process(ProcessContext context) {
      if (context.element() instanceof Discardable d) context.output(TO_DISCARD, d);
      else if (context.element() instanceof Retriable r) context.output(TO_RETRY, r);
      else
        throw new IllegalArgumentException(
            "Unexpected processing error type: " + context.element().toString());
    }
  }

  public static class ErrorHandler<T>
      extends SimpleFunction<WithFailures.ExceptionElement<T>, ProcessingError> {
    @Override
    public ProcessingError apply(WithFailures.ExceptionElement<T> f) {
      if (f.exception() instanceof ContentIdExtractError cexe
          && f.element() instanceof PubsubMessage pMsg) {
        return new Discardable(new String(pMsg.getPayload()), cexe);
      } else if ((f.exception() instanceof DocumentIdError dexe
          && f.element() instanceof Types.Transport t)) {
        return createMaybeRetriable(dexe, t);
      } else if (f.exception() instanceof DocumentContentError dcexe
          && f.element() instanceof Types.Transport t) {
        return createMaybeRetriable(dcexe, t);
      } else {
        return new Discardable(f.element().toString(), f.exception());
      }
    }
  }

  static ProcessingError createMaybeRetriable(Exception ex, Types.Transport t) {
    if (MAX_RETRIES < t.retriesCount()) {
      return new Discardable(t.contentId(), ex);
    } else {
      return new Retriable(t.contentId(), t.metadata(), t.retriesCount() + 1);
    }
  }
}
