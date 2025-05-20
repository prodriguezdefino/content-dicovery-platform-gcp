package com.google.cloud.pso.rag.content;

import com.google.cloud.pso.rag.common.InteractionHelper;
import com.google.cloud.pso.rag.executor.Result;
import com.google.cloud.pso.rag.util.ErrorResponse;
import dev.langchain4j.data.document.splitter.RecursiveCharacterTextSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.DocumentSplitters;
// import dev.langchain4j.model.openai.OpenAiTokenizer; // Not used for local splitting
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class LangChainChunks {

    private LangChainChunks() {}

    // modelName is not used for local splitting but kept for consistency with Gemini.ChunkRequest
    public record LangChainRecursiveCharacterSplitterRequest(
            String text,
            String modelName,
            Optional<Integer> maxSegmentSizeInChars,
            Optional<Integer> maxOverlapSizeInChars) implements Chunks.ChunkRequest {
        @Override
        public String content() { // Keep content() for compatibility with Chunks.ChunkRequest if it's used elsewhere.
            return text;
        }
    }

    public record LangChainChunkResponse(List<String> chunks) implements Chunks.ChunkResponse {
        @Override
        public List<String> chunks() {
            return chunks;
        }
    }

    public static CompletableFuture<Result<? extends Chunks.ChunkResponse, ErrorResponse>> extractChunks(LangChainRecursiveCharacterSplitterRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            int segmentSize = request.maxSegmentSizeInChars().orElse(500);
            int overlapSize = request.maxOverlapSizeInChars().orElse(50);

            // OpenAiTokenizer could be used here if we want to split by tokens:
            // RecursiveCharacterTextSplitter splitter = DocumentSplitters.recursive(segmentSize, overlapSize, new OpenAiTokenizer(request.modelName()));
            RecursiveCharacterTextSplitter splitter = DocumentSplitters.recursive(segmentSize, overlapSize);

            List<TextSegment> segments = splitter.split(request.text());
            List<String> chunks = segments.stream()
                                          .map(TextSegment::text)
                                          .collect(Collectors.toList());

            return Result.success(new LangChainChunkResponse(chunks));
        }, InteractionHelper.EXEC)
        .exceptionally(error -> {
            // You can log the error here if needed, e.g., using a logger
            // LOGGER.error("Error during LangChain chunking", error);
            // Ensure the ErrorResponse constructor is used if it expects a message and a Throwable.
            // If ErrorResponse is a record/class that simply takes a string, then adjust accordingly.
            // Assuming ErrorResponse can be created with a message and a throwable for context.
            // If not, new ErrorResponse("Error during LangChain chunking: " + error.getMessage()) might be more appropriate
            // or just new ErrorResponse("Error during LangChain chunking.")
            // For now, sticking to the simpler new ErrorResponse("message", throwable) if that's how it's defined.
            // The previous code used: new ErrorResponse("Error during LangChain chunking: " + e.getMessage(), e)
            // So, to keep it consistent:
            return Result.failure(new ErrorResponse("Error during LangChain chunking: " + error.getMessage(), error));
        });
    }
}
