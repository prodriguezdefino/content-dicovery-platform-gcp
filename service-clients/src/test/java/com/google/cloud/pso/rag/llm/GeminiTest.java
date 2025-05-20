package com.google.cloud.pso.rag.llm;

import com.google.cloud.pso.rag.common.GCPEnvironment;
import com.google.cloud.pso.rag.common.Models;
import com.google.genai.api.ChatFutures;
import com.google.genai.api.ChatSession;
import com.google.genai.api.GenerativeModelFutures;
import com.google.genai.generativelanguage.v1beta.GenerativeModel;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerationConfig;
import com.google.genai.types.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test; // Added for @Test
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any; // Added for any()
import static org.mockito.ArgumentMatchers.eq; // Added for eq()
import static org.junit.jupiter.api.Assertions.assertEquals; // Added for assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse; // Added for assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue; // Added for assertTrue
import static org.junit.jupiter.api.Assertions.assertNotNull; // Added for assertNotNull

@ExtendWith(MockitoExtension.class)
public class GeminiTest {

    @Mock
    private GCPEnvironment.Config mockConfig;

    @Mock
    private GenerativeModel mockGenerativeModel;

    @Mock
    private ChatSession mockChatSession;

    @Mock
    private ChatFutures mockChatFutures;

    @Mock
    private GenerativeModelFutures mockGenerativeModelFutures;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock static method calls
        // It's important to mock static methods within a try-with-resources block or clear them afterwards
        // to avoid test pollution. However, for simplicity in this setup, we'll mock them directly.
        // Consider using MockedStatic for more robust static mocking.
        Mockito.mockStatic(GCPEnvironment.class);
        Mockito.when(GCPEnvironment.config()).thenReturn(mockConfig);

        Mockito.mockStatic(Models.class);
        Mockito.when(Models.gemini()).thenReturn(mockGenerativeModel);

        // Mock behavior of generativeModel
        Mockito.when(mockGenerativeModel.chats()).thenReturn(mockChatFutures);
        Mockito.when(mockChatFutures.create()).thenReturn(mockChatSession);
        Mockito.when(mockGenerativeModel.models()).thenReturn(mockGenerativeModelFutures);
    }

  @Test
  void testChat() throws Exception {
    // 1. Define ChatRequest
    Gemini.ChatRequest request =
        new Gemini.ChatRequest(
            "gemini-pro", 
            java.util.Optional.of("Test context"), 
            java.util.List.of(new LLM.Exchange("user", "Hello")), 
            new LLM.Parameters(java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty()) 
            );

    // 2. Create mock GenerateContentResponse
    // Ensure com.google.genai.types.Candidate is imported for this section
    GenerateContentResponse mockApiResponse =
        GenerateContentResponse.newBuilder()
            .addCandidate(
                Candidate.newBuilder() 
                    .setContent(Content.newBuilder().addPart(Part.fromText("Test chat response"))))
            .build();

    // 3. Mock behavior specific to this test case
    Mockito.when(mockChatFutures.create(request.model())).thenReturn(mockChatSession);
    Mockito.when(mockChatSession.sendMessage(any(java.util.List.class), any(GenerationConfig.class))) // Use static import for any
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(mockApiResponse));

    // 4. Call Gemini.chat()
    java.util.concurrent.CompletableFuture<com.google.cloud.pso.rag.common.Result<? extends LLM.ChatResponse, com.google.cloud.pso.rag.common.Result.ErrorResponse>> futureResult =
        Gemini.chat(request);
    com.google.cloud.pso.rag.common.Result<? extends LLM.ChatResponse, com.google.cloud.pso.rag.common.Result.ErrorResponse> chatResult = futureResult.get();

    // 5. Assertions
    assertTrue(chatResult.isSuccess(), "Chat result should be successful"); // Use static import
    assertEquals(
        "Test chat response",
        chatResult.success().answer().content(),
        "Chat response content mismatch"); // Use static import
    assertFalse(
        chatResult.success().blockReason().isPresent(), 
        "Block reason should not be present for a successful response"); // Use static import


    // 6. Verification
    Mockito.verify(mockChatFutures).create(request.model());
    Mockito.verify(mockChatSession).sendMessage(any(java.util.List.class), any(GenerationConfig.class)); // Use static import
  }

  @Test
  void testSummarize() throws Exception {
    // 1. Define SummarizeRequest
    Gemini.SummarizeRequest request =
        new Gemini.SummarizeRequest(
            "gemini-pro-vision", // Model name
            java.util.List.of("This is a long text to summarize."), // Content to summarize
            new LLM.Parameters(java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty()) // Params
            );

    // 2. Create mock GenerateContentResponse for summarization
    GenerateContentResponse mockApiResponse =
        GenerateContentResponse.newBuilder()
            .addCandidate(
                Candidate.newBuilder()
                    .setContent(Content.newBuilder()
                        .addPart(Part.fromText("Summarized text"))))
            .build();

    // 3. Mock behavior specific to this test case
    // mockGenerativeModel.models() is already mocked in setUp to return mockGenerativeModelFutures
    Mockito.when(mockGenerativeModelFutures.generateContent(
            any(String.class), 
            any(Content.class), 
            any(GenerationConfig.class) 
        ))
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(mockApiResponse));

    // 4. Call Gemini.summarize()
    java.util.concurrent.CompletableFuture<com.google.cloud.pso.rag.common.Result<? extends LLM.SummarizationResponse, com.google.cloud.pso.rag.common.Result.ErrorResponse>> futureResult =
        Gemini.summarize(request);
    com.google.cloud.pso.rag.common.Result<? extends LLM.SummarizationResponse, com.google.cloud.pso.rag.common.Result.ErrorResponse> summarizeResult = futureResult.get();

    // 5. Assertions
    assertTrue(summarizeResult.isSuccess(), "Summarize result should be successful");
    assertEquals(
        "Summarized text",
        summarizeResult.success().content(),
        "Summarize response content mismatch");

    // 6. Verification
    Mockito.verify(mockGenerativeModelFutures)
        .generateContent(
            eq(request.model()), 
            any(Content.class),
            any(GenerationConfig.class));
  }

  @Test
  void testChat_errorHandling() throws Exception {
    // 1. Define ChatRequest
    Gemini.ChatRequest request =
        new Gemini.ChatRequest(
            "gemini-pro-error",
            java.util.Optional.empty(),
            java.util.List.of(new LLM.Exchange("user", "Error me")),
            new LLM.Parameters(java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty()));

    // 2. Define the exception to be thrown
    RuntimeException simulatedException = new RuntimeException("Simulated API error");

    // 3. Mock behavior for error
    Mockito.when(mockChatFutures.create(request.model())).thenReturn(mockChatSession);
    Mockito.when(mockChatSession.sendMessage(any(java.util.List.class), any(com.google.genai.types.GenerationConfig.class)))
        .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(simulatedException));

    // 4. Call Gemini.chat()
    java.util.concurrent.CompletableFuture<com.google.cloud.pso.rag.common.Result<? extends LLM.ChatResponse, com.google.cloud.pso.rag.common.Result.ErrorResponse>> futureResult =
        Gemini.chat(request);
    com.google.cloud.pso.rag.common.Result<? extends LLM.ChatResponse, com.google.cloud.pso.rag.common.Result.ErrorResponse> chatResult = futureResult.get();

    // 5. Assertions for failure
    assertTrue(chatResult.isFailure(), "Chat result should be a failure");
    assertNotNull(chatResult.failure(), "Failure object should not be null");
    assertEquals(
        "Error while generating chat request.", // This is the generic error message from Gemini.java
        chatResult.failure().message(),
        "Error message mismatch");
    assertTrue(
        chatResult.failure().error().isPresent(),
        "Underlying error should be present");
    // Check if the cause of the error is the one we simulated.
    // The CompletionException wraps the actual exception from CompletableFuture.failedFuture()
    assertTrue(chatResult.failure().error().get().getCause() instanceof RuntimeException, "Cause should be RuntimeException");
    assertEquals("Simulated API error", chatResult.failure().error().get().getCause().getMessage(), "Cause message mismatch");


    // 6. Verification
    Mockito.verify(mockChatFutures).create(request.model());
    Mockito.verify(mockChatSession).sendMessage(any(java.util.List.class), any(com.google.genai.types.GenerationConfig.class));
  }

  @Test
  void testSummarize_errorHandling() throws Exception {
    // 1. Define SummarizeRequest
    Gemini.SummarizeRequest request =
        new Gemini.SummarizeRequest(
            "gemini-pro-vision-error",
            java.util.List.of("Text to cause error during summarization."),
            new LLM.Parameters(java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty()));

    // 2. Define the exception to be thrown
    RuntimeException simulatedException = new RuntimeException("Simulated API error for summarize");

    // 3. Mock behavior for error
    Mockito.when(mockGenerativeModelFutures.generateContent(
            any(String.class),
            any(com.google.genai.types.Content.class),
            any(com.google.genai.types.GenerationConfig.class)))
        .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(simulatedException));

    // 4. Call Gemini.summarize()
    java.util.concurrent.CompletableFuture<com.google.cloud.pso.rag.common.Result<? extends LLM.SummarizationResponse, com.google.cloud.pso.rag.common.Result.ErrorResponse>> futureResult =
        Gemini.summarize(request);
    com.google.cloud.pso.rag.common.Result<? extends LLM.SummarizationResponse, com.google.cloud.pso.rag.common.Result.ErrorResponse> summarizeResult = futureResult.get();

    // 5. Assertions for failure
    assertTrue(summarizeResult.isFailure(), "Summarize result should be a failure");
    assertNotNull(summarizeResult.failure(), "Failure object should not be null");
    assertEquals(
        "Error while generating summarization request.", // Generic error from Gemini.java
        summarizeResult.failure().message(),
        "Error message mismatch");
    assertTrue(
        summarizeResult.failure().error().isPresent(),
        "Underlying error should be present");
    assertTrue(summarizeResult.failure().error().get().getCause() instanceof RuntimeException, "Cause should be RuntimeException");
    assertEquals("Simulated API error for summarize", summarizeResult.failure().error().get().getCause().getMessage(), "Cause message mismatch");

    // 6. Verification
    Mockito.verify(mockGenerativeModelFutures)
        .generateContent(
            eq(request.model()),
            any(com.google.genai.types.Content.class),
            any(com.google.genai.types.GenerationConfig.class));
  }
}
