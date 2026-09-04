package com.nguyenvu.lopet.llms.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class LLMDtos {

    public static final String CONVERSATION_ID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    public record AskRequest(
            @NotBlank(message = "message must not be blank")
            @Size(max = 2000, message = "message must be at most 2000 characters")
            String message,

            @Pattern(regexp = CONVERSATION_ID_PATTERN,
                    message = "conversationId must be a UUID issued by the server on a previous question")
            String conversationId) {
    }

    public record AskResponse(
            String conversationId,
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

    private LLMDtos() {
    }
}
