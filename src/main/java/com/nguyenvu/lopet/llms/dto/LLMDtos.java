package com.nguyenvu.lopet.llms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class LLMDtos {

    private LLMDtos() {
    }

    public record AskRequest(
            @NotBlank(message = "message must not be blank")
            @Size(max = 2000, message = "message must be at most 2000 characters")
            String message
    ) {
    }

    public record AskResponse(
            String answer,
            ModelUsage usage) {
    }

    public record ModelUsage(
            String model,
            String finishReason,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            long latencyMs) {
    }

    public record ToolInfo(String name, String description) {
    }

    public record ToolListResponse(int total, List<ToolInfo> tools) {
    }
}
