package com.google.cloud.pso.rag.content;

import static org.junit.jupiter.api.Assertions.*;

import com.google.cloud.pso.rag.executor.Result;
import com.google.cloud.pso.rag.util.ErrorResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class LangChainChunksTest {

    private final String sampleText700 =
        "This is a long string designed to test the chunking functionality of the LangChainChunks class. " + // 100
        "It needs to be sufficiently lengthy to be split into multiple segments by the RecursiveCharacterTextSplitter. " + // 110
        "The splitter is configured with a maximum segment size of 500 characters and an overlap of 50 characters. " + // 108
        "Therefore, this string, which is intentionally made to be 700 characters long, should ideally result in two distinct chunks. " + // 122
        "The first chunk should be approximately 500 characters long, and the second chunk should contain the remaining part of the text. " + // 130
        "Crucially, there should be an overlap of 50 characters between the end of the first chunk and the beginning of the second chunk. " + // 130
        "This overlap ensures context is maintained across chunks."; // 60

    @Test
    void testExtractChunks_simpleText_shouldReturnChunks() throws ExecutionException, InterruptedException {
        assertEquals(700, sampleText700.length(), "Sample text length should be 700 characters for this test.");

        LangChainChunks.LangChainRecursiveCharacterSplitterRequest request =
            new LangChainChunks.LangChainRecursiveCharacterSplitterRequest(sampleText700, "default-model", Optional.empty(), Optional.empty());

        CompletableFuture<Result<? extends Chunks.ChunkResponse, ErrorResponse>> futureResult =
                LangChainChunks.extractChunks(request);

        Result<? extends Chunks.ChunkResponse, ErrorResponse> result = futureResult.get();

        assertTrue(result.isSuccess(), "The chunking process should be successful.");
        assertNotNull(result.getSuccessValue(), "The success value (ChunkResponse) should not be null.");
        
        Chunks.ChunkResponse response = result.getSuccessValue();
        List<String> chunks = response.chunks();

        assertNotNull(chunks, "The list of chunks should not be null.");
        assertFalse(chunks.isEmpty(), "The list of chunks should not be empty.");
        
        // With a 700 char string, default 500 char limit, and default 50 char overlap:
        // Chunk 1: text[0...499]
        // Chunk 2: text[450...699] 
        assertEquals(2, chunks.size(), "Expected 2 chunks for a 700 character string with 500/50 splitting.");

        String chunk1 = chunks.get(0);
        String chunk2 = chunks.get(1);

        assertEquals(500, chunk1.length(), "Chunk 1 length should be 500 characters.");
        assertEquals(250, chunk2.length(), "Chunk 2 length should be 250 characters (700 - 500 + 50 overlap).");


        String overlapFromChunk1 = chunk1.substring(chunk1.length() - 50);
        String overlapInChunk2 = chunk2.substring(0, 50);
        assertEquals(overlapFromChunk1, overlapInChunk2, "The overlap between chunk 1 and chunk 2 should match.");

        assertEquals(sampleText700, chunk1.substring(0, 500 - 50) + chunk2, "Combined chunks (accounting for overlap) should match original text");
    }

    @Test
    void testExtractChunks_customSizeAndOverlap_shouldReturnChunksAsConfigured() throws ExecutionException, InterruptedException {
        assertEquals(700, sampleText700.length(), "Sample text length should be 700 characters for this test.");
        int customChunkSize = 300;
        int customOverlap = 30;

        LangChainChunks.LangChainRecursiveCharacterSplitterRequest request =
            new LangChainChunks.LangChainRecursiveCharacterSplitterRequest(sampleText700, "custom-model", Optional.of(customChunkSize), Optional.of(customOverlap));

        CompletableFuture<Result<? extends Chunks.ChunkResponse, ErrorResponse>> futureResult =
                LangChainChunks.extractChunks(request);
        Result<? extends Chunks.ChunkResponse, ErrorResponse> result = futureResult.get();

        assertTrue(result.isSuccess(), "The chunking process with custom sizes should be successful.");
        assertNotNull(result.getSuccessValue(), "The success value (ChunkResponse) should not be null for custom sizes.");

        Chunks.ChunkResponse response = result.getSuccessValue();
        List<String> chunks = response.chunks();

        assertNotNull(chunks, "The list of chunks should not be null for custom sizes.");
        
        // Chunk 1: 0-299 (300 chars)
        // Chunk 2: 270-569 (300 chars) (starts at 300-30=270, ends at 270+300=570)
        // Chunk 3: 540-699 (160 chars) (starts at 570-30=540, ends at 540+remaining=540+160=700)
        assertEquals(3, chunks.size(), "Expected 3 chunks for 700 chars with 300/30 splitting.");

        String chunk1 = chunks.get(0);
        String chunk2 = chunks.get(1);
        String chunk3 = chunks.get(2);

        assertEquals(customChunkSize, chunk1.length(), "Chunk 1 length should be custom chunk size.");
        assertEquals(customChunkSize, chunk2.length(), "Chunk 2 length should be custom chunk size.");
        assertEquals(sampleText700.length() - (2 * customChunkSize) + (2 * customOverlap) , chunk3.length(), "Chunk 3 length should be the remainder."); // 700 - 600 + 60 = 160

        String overlap1to2FromChunk1 = chunk1.substring(customChunkSize - customOverlap);
        String overlap1to2InChunk2 = chunk2.substring(0, customOverlap);
        assertEquals(overlap1to2FromChunk1, overlap1to2InChunk2, "Overlap between chunk 1 and 2 should match.");

        String overlap2to3FromChunk2 = chunk2.substring(customChunkSize - customOverlap);
        String overlap2to3InChunk3 = chunk3.substring(0, customOverlap);
        assertEquals(overlap2to3FromChunk2, overlap2to3InChunk3, "Overlap between chunk 2 and 3 should match.");
        
        String reconstructedText = chunk1.substring(0, customChunkSize - customOverlap) + 
                                   chunk2.substring(0, customChunkSize - customOverlap) + 
                                   chunk3;
        assertEquals(sampleText700, reconstructedText, "Combined chunks (accounting for overlap) should match original text with custom sizes.");
    }

    @Test
    void testExtractChunks_textShorterThanSize_shouldReturnSingleChunk() throws ExecutionException, InterruptedException {
        String shortText = "This is a short text, much less than 500 characters."; // 52 chars
        assertEquals(52, shortText.length());

        LangChainChunks.LangChainRecursiveCharacterSplitterRequest request =
            new LangChainChunks.LangChainRecursiveCharacterSplitterRequest(shortText, "short-text-model", Optional.empty(), Optional.empty());

        CompletableFuture<Result<? extends Chunks.ChunkResponse, ErrorResponse>> futureResult =
                LangChainChunks.extractChunks(request);
        Result<? extends Chunks.ChunkResponse, ErrorResponse> result = futureResult.get();

        assertTrue(result.isSuccess(), "Chunking short text should be successful.");
        assertNotNull(result.getSuccessValue(), "Success value for short text should not be null.");

        Chunks.ChunkResponse response = result.getSuccessValue();
        List<String> chunks = response.chunks();

        assertNotNull(chunks, "Chunks list for short text should not be null.");
        assertEquals(1, chunks.size(), "Expected 1 chunk for text shorter than default chunk size.");
        assertEquals(shortText, chunks.get(0), "The single chunk should match the original short text.");
        assertEquals(shortText.length(), chunks.get(0).length(), "The single chunk length should match original short text length.");
    }
}
