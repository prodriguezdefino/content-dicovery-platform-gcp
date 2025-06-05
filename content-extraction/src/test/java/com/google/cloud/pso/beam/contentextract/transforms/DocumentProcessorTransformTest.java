package com.google.cloud.pso.beam.contentextract.transforms;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.pso.beam.contentextract.Types;
import com.google.cloud.pso.beam.contentextract.transforms.DocumentProcessorTransform.DistributeByContentDoFn;
import com.google.cloud.pso.rag.common.Ingestion;
import com.google.cloud.pso.rag.common.Result;
import java.util.List;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.values.TupleTag;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

@RunWith(JUnit4.class)
public class DocumentProcessorTransformTest {

  @Test
  public void referencesContent_withVideoType_outputsCorrectContent() {
    DistributeByContentDoFn doFn = new DistributeByContentDoFn();
    DoFn.ProcessContext mockContext = mock(DoFn.ProcessContext.class);
    ArgumentCaptor<Types.Content> contentCaptor = ArgumentCaptor.forClass(Types.Content.class);

    String videoUrl = "http://example.com/video.mp4";
    Ingestion.Reference videoReference =
        new Ingestion.Reference(videoUrl, Ingestion.SupportedType.VIDEO);
    List<Ingestion.Reference> references = List.of(videoReference);

    Result<Boolean, Exception> result =
        DistributeByContentDoFn.referencesContent(references, mockContext);

    assertThat(result.succeeded()).isTrue();
    verify(mockContext)
        .output(
            ArgumentCaptor.forClass(TupleTag.class).capture(), contentCaptor.capture());

    Types.Content capturedContent = contentCaptor.getValue();
    assertThat(capturedContent.key()).isEqualTo(videoUrl);
    assertThat(capturedContent.content()).containsExactly(videoUrl);
    // As per the implementation, VIDEO type results in VIDEO type in Types.Content
    assertThat(capturedContent.type()).isEqualTo(Ingestion.SupportedType.VIDEO);
  }

  @Test
  public void referencesContent_withVideoLinkType_outputsCorrectContent() {
    DistributeByContentDoFn doFn = new DistributeByContentDoFn();
    DoFn.ProcessContext mockContext = mock(DoFn.ProcessContext.class);
    ArgumentCaptor<Types.Content> contentCaptor = ArgumentCaptor.forClass(Types.Content.class);

    String videoLinkUrl = "http://example.com/video_link.mp4";
    Ingestion.Reference videoLinkReference =
        new Ingestion.Reference(videoLinkUrl, Ingestion.SupportedType.VIDEO_LINK);
    List<Ingestion.Reference> references = List.of(videoLinkReference);

    Result<Boolean, Exception> result =
        DistributeByContentDoFn.referencesContent(references, mockContext);

    assertThat(result.succeeded()).isTrue();
    verify(mockContext)
        .output(
            ArgumentCaptor.forClass(TupleTag.class).capture(), contentCaptor.capture());

    Types.Content capturedContent = contentCaptor.getValue();
    assertThat(capturedContent.key()).isEqualTo(videoLinkUrl);
    assertThat(capturedContent.content()).containsExactly(videoLinkUrl);
    // As per the implementation, VIDEO_LINK type results in VIDEO_LINK type in Types.Content
    assertThat(capturedContent.type()).isEqualTo(Ingestion.SupportedType.VIDEO_LINK);
  }

  @Test
  public void referencesContent_withPdfType_outputsCorrectContentWithPdfLink() {
    DistributeByContentDoFn doFn = new DistributeByContentDoFn();
    DoFn.ProcessContext mockContext = mock(DoFn.ProcessContext.class);
    ArgumentCaptor<Types.Content> contentCaptor = ArgumentCaptor.forClass(Types.Content.class);

    String pdfUrl = "http://example.com/doc.pdf";
    Ingestion.Reference pdfReference =
        new Ingestion.Reference(pdfUrl, Ingestion.SupportedType.PDF);
    List<Ingestion.Reference> references = List.of(pdfReference);

    Result<Boolean, Exception> result =
        DistributeByContentDoFn.referencesContent(references, mockContext);

    assertThat(result.succeeded()).isTrue();
    verify(mockContext)
        .output(
            ArgumentCaptor.forClass(TupleTag.class).capture(), contentCaptor.capture());

    Types.Content capturedContent = contentCaptor.getValue();
    assertThat(capturedContent.key()).isEqualTo(pdfUrl);
    assertThat(capturedContent.content()).containsExactly(pdfUrl);
    // As per the implementation, PDF type results in PDF_LINK type in Types.Content
    assertThat(capturedContent.type()).isEqualTo(Ingestion.SupportedType.PDF_LINK);
  }
}
