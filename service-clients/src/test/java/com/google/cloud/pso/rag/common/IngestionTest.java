package com.google.cloud.pso.rag.common;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.cloud.pso.rag.common.Ingestion.SupportedType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class IngestionTest {

  @Test
  public void supportedType_video_hasCorrectMimeType() {
    assertThat(SupportedType.VIDEO.mimeType()).isEqualTo("video/mp4");
  }

  @Test
  public void supportedType_videoLink_hasCorrectMimeType() {
    assertThat(SupportedType.VIDEO_LINK.mimeType()).isEqualTo("link/mp4");
  }

  @Test
  public void fromString_videoMimeType_returnsVideo() {
    assertThat(SupportedType.fromString("video/mp4")).isEqualTo(SupportedType.VIDEO);
  }

  @Test
  public void fromString_videoLinkMimeType_returnsVideoLink() {
    assertThat(SupportedType.fromString("link/mp4")).isEqualTo(SupportedType.VIDEO_LINK);
  }

  @Test
  public void toLink_video_returnsVideoLink() {
    assertThat(SupportedType.VIDEO.toLink()).isEqualTo(SupportedType.VIDEO_LINK);
  }

  @Test
  public void toLink_videoLink_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              SupportedType.VIDEO_LINK.toLink();
            });
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(SupportedType.VIDEO_LINK.name() + " does not have a link version.");
  }

  @Test
  public void toLink_pdfLink_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              SupportedType.PDF_LINK.toLink();
            });
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(SupportedType.PDF_LINK.name() + " does not have a link version.");
  }

  @Test
  public void fromString_invalidType_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          SupportedType.fromString("invalid/type");
        });
  }
}
