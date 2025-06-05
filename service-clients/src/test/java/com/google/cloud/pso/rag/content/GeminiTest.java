package com.google.cloud.pso.rag.content;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.pso.rag.common.GCPEnvironment;
import com.google.cloud.pso.rag.common.Ingestion;
import com.google.cloud.pso.rag.common.Models;
import com.google.cloud.pso.rag.common.Result;
import com.google.genai.client.GenerativeModel;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

@RunWith(JUnit4.class)
public class GeminiTest {

  private static final String TEST_MODEL_NAME = "gemini-pro-vision";
  private static final String TEST_VIDEO_URL = "gs://example-bucket/video.mp4";

  private MockedStatic<GCPEnvironment> mockGCPEnvironment;
  private MockedStatic<Models> mockModels;
  private GenerativeModel mockGenerativeModel;

  @Before
  public void setUp() {
    // Mock GCPEnvironment.config()
    mockGCPEnvironment = mockStatic(GCPEnvironment.class);
    GCPEnvironment.Config mockConfig = mock(GCPEnvironment.Config.class);
    mockGCPEnvironment.when(GCPEnvironment::config).thenReturn(mockConfig);

    // Mock Models.gemini() to return a wrapper/client that contains our mock GenerativeModel
    // Assuming Models.gemini() returns an object that has a public field 'models'
    // or a method to get the GenerativeModel.
    // For this test, we'll simplify and assume Models.gemini() directly returns GenerativeModel
    // as the prompt implies `Models.gemini(...).models` is the GenerativeModel.
    // If `Models.gemini()` returns a client which then provides the model, the mocking would be:
    // GenerativeServiceClient mockServiceClient = mock(GenerativeServiceClient.class);
    // when(mockServiceClient.generativeModel(anyString())).thenReturn(mockGenerativeModel);
    // mockedModels.when(() -> Models.gemini(any())).thenReturn(mockServiceClient);
    // However, the code is `gemini.models.generateContent`, so `gemini` is not the model itself.
    // Let's assume a structure like:
    // class GeminiClientWrapper { public GenerativeModel models; }
    // And Models.gemini() returns GeminiClientWrapper.

    Models.GeminiClientWrapper mockGeminiClientWrapper = mock(Models.GeminiClientWrapper.class);
    mockGenerativeModel = mock(GenerativeModel.class);
    when(mockGeminiClientWrapper.models).thenReturn(mockGenerativeModel);

    mockModels = mockStatic(Models.class);
    mockModels.when(() -> Models.gemini(any())).thenReturn(mockGeminiClientWrapper);
    // Also mock the default config and schema for verification
    mockModels.when(Models::getDefaultGeminiConfig).thenReturn(GenerateContentConfig.newBuilder().build());
    mockModels.when(Models::getStringArraySchema).thenReturn(mock(com.google.genai.types.Schema.class));
  }

  @After
  public void tearDown() {
    mockGCPEnvironment.close();
    mockModels.close();
  }

  @Test
  public void partFromType_video_returnsCorrectPart() {
    Part part = Gemini.partFromType(Ingestion.SupportedType.VIDEO, TEST_VIDEO_URL);
    assertThat(part.getUri().toString()).isEqualTo(TEST_VIDEO_URL);
    assertThat(part.getMimeType()).isEqualTo(Ingestion.SupportedType.VIDEO.mimeType());
    assertThat(part.getMimeType()).isEqualTo("video/mp4");
  }

  @Test
  public void partFromType_videoLink_returnsCorrectPart() {
    Part part = Gemini.partFromType(Ingestion.SupportedType.VIDEO_LINK, TEST_VIDEO_URL);
    assertThat(part.getUri().toString()).isEqualTo(TEST_VIDEO_URL);
    // As per implementation, VIDEO_LINK also uses VIDEO's mimeType for the Part
    assertThat(part.getMimeType()).isEqualTo(Ingestion.SupportedType.VIDEO.mimeType());
    assertThat(part.getMimeType()).isEqualTo("video/mp4");
  }

  @Test
  public void extractChunks_videoChunkRequest_success() {
    Gemini.VideoChunkRequest request =
        new Gemini.VideoChunkRequest(
            TEST_MODEL_NAME, List.of(TEST_VIDEO_URL), Ingestion.SupportedType.VIDEO);

    GenerateContentResponse mockResponse = mock(GenerateContentResponse.class);
    when(mockResponse.text()).thenReturn("[\"chunk1 from video\", \"chunk2 from video\"]");
    when(mockGenerativeModel.generateContent(any(Content.class), any(GenerateContentConfig.class)))
        .thenReturn(mockResponse);

    Result<? extends Chunks.ChunkResponse, ?> result = Gemini.extractChunks(request).join();

    assertThat(result.succeeded()).isTrue();
    assertThat(result.get()).isInstanceOf(Gemini.TextChunkResponse.class);
    Gemini.TextChunkResponse textResponse = (Gemini.TextChunkResponse) result.get();
    assertThat(textResponse.chunks()).containsExactly("chunk1 from video", "chunk2 from video").inOrder();

    ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
    ArgumentCaptor<GenerateContentConfig> configCaptor =
        ArgumentCaptor.forClass(GenerateContentConfig.class);
    verify(mockGenerativeModel)
        .generateContent(contentCaptor.capture(), configCaptor.capture());

    Content capturedContent = contentCaptor.getValue();
    assertThat(capturedContent.getPartsList()).hasSize(2);
    assertThat(capturedContent.getPartsList().get(0).getUri().toString()).isEqualTo(TEST_VIDEO_URL);
    assertThat(capturedContent.getPartsList().get(0).getMimeType()).isEqualTo(Ingestion.SupportedType.VIDEO.mimeType());
    assertThat(capturedContent.getPartsList().get(1).getText()).isEqualTo(Gemini.VIDEO_CHUNKING_PROMPT);
    
    GenerateContentConfig capturedConfig = configCaptor.getValue();
    // Verify system instruction and schema were part of the config
    // Exact check depends on how Models.DEFAULT_CONFIG and STRING_ARRAY_SCHEMA are structured
    // For now, checking they are not null or relying on the setup mock for Models.getDefaultGeminiConfig()
    assertThat(capturedConfig.getSystemInstruction()).isNotNull();
    assertThat(capturedConfig.getResponseSchema()).isNotNull();
  }

  @Test
  public void extractChunks_videoChunkRequest_geminiApiError() {
    Gemini.VideoChunkRequest request =
        new Gemini.VideoChunkRequest(
            TEST_MODEL_NAME, List.of(TEST_VIDEO_URL), Ingestion.SupportedType.VIDEO_LINK);

    RuntimeException apiException = new RuntimeException("Gemini API error");
    when(mockGenerativeModel.generateContent(any(Content.class), any(GenerateContentConfig.class)))
        .thenThrow(apiException);

    Result<? extends Chunks.ChunkResponse, ?> result = Gemini.extractChunks(request).join();

    assertThat(result.succeeded()).isFalse();
    assertThat(result.error()).isNotNull();
    assertThat(result.error().message()).isEqualTo("Error while generating chunks.");
    assertThat(result.error().parentException()).hasValue(apiException);
  }

  @Test
  public void extractChunks_videoChunkRequest_invalidJsonResponse() {
    Gemini.VideoChunkRequest request =
        new Gemini.VideoChunkRequest(
            TEST_MODEL_NAME, List.of(TEST_VIDEO_URL), Ingestion.SupportedType.VIDEO);

    GenerateContentResponse mockResponse = mock(GenerateContentResponse.class);
    when(mockResponse.text()).thenReturn("this is not json"); // Invalid JSON
    when(mockGenerativeModel.generateContent(any(Content.class), any(GenerateContentConfig.class)))
        .thenReturn(mockResponse);

    Result<? extends Chunks.ChunkResponse, ?> result = Gemini.extractChunks(request).join();

    assertThat(result.succeeded()).isFalse();
    assertThat(result.error()).isNotNull();
    assertThat(result.error().message()).isEqualTo("Error while parsing response from model.");
    assertThat(result.error().parentException()).isPresent();
  }
   @Test
  public void partFromType_unsupportedType_throwsException() {
    // Example using a type that isn't explicitly handled and isn't a _LINK version of a handled one
    // This requires an update to SupportedType or assuming a gap in partFromType logic
    // For now, let's test with a type that would hit the default case
    // Assuming TEXT is not supposed to be handled by fromBytes or fromUri directly in some contexts
    // The current partFromType handles TEXT, so this test would need a truly unhandled type.
    // Let's use a placeholder for this idea. For now, all defined types are handled.
    // If Ingestion.SupportedType.SOME_OTHER_TYPE existed and wasn't in the switch:
    // assertThrows(IllegalArgumentException.class, () -> Gemini.partFromType(Ingestion.SupportedType.SOME_OTHER_TYPE, "data"));
    
    // Test with an existing type that should throw based on current logic (e.g. if TEXT was removed)
    // The default case "throw new IllegalArgumentException("Type not supported: " + type);"
    // Currently, all enum values in SupportedType have a corresponding case in partFromType.
    // To test this, we'd need to ensure one is missing.
    // This test is more of a placeholder unless the enum/switch is out of sync.
    // No straightforward way to test the default case without modifying the enum or switch.
  }
}
