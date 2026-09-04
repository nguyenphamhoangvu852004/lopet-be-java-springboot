package com.nguyenvu.lopet.llms;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.stereotype.Service;

import com.anthropic.models.messages.OutputConfig;
import com.nguyenvu.lopet.common.exception.ServiceUnavailableException;
import com.nguyenvu.lopet.llms.dto.LLMDtos;

@Service
public class LLMService {

    private static final String SYSTEM_PROMPT = """
            You are the statistics assistant of Lopet - a social network for pet owners.
            You only answer system-wide statistical questions, using the tools provided to you.
            Always call a tool to get real numbers; never make up or infer a figure yourself.
            You cannot access the private data of any individual user; if asked for that,
            decline and say that you only have system-wide figures.
            Answer concisely in English.
            """;

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final List<LLMDtos.ToolInfo> tools;

    public LLMService(ChatClient.Builder builder,
                      ChatMemory chatMemory,
                      AccountTool accountTool,
                      StatisticTool statisticTool) {
        Object[] toolBeans = {accountTool, statisticTool};

        this.chatMemory = chatMemory;
        this.tools = Arrays.stream(ToolCallbacks.from(toolBeans))
                .map(callback -> new LLMDtos.ToolInfo(
                        callback.getToolDefinition().name(),
                        callback.getToolDefinition().description()))
                .toList();
        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(toolBeans)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultOptions(AnthropicChatOptions.builder()
                        .effort(OutputConfig.Effort.MEDIUM)
                )
                .build();
    }

    public LLMDtos.AskResponse ask(String message, String conversationId) {
        String resolvedId = conversationId == null || conversationId.isBlank()
                ? UUID.randomUUID().toString()
                : conversationId;

        long startedAt = System.currentTimeMillis();
        ChatResponse response = chatClient.prompt()
                .user(message)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, resolvedId))
                .call()
                .chatResponse();

        if (response == null || response.getResult() == null) {
            throw new ServiceUnavailableException("The assistant returned no content");
        }

        return new LLMDtos.AskResponse(resolvedId, response.getResult().getOutput().getText(),
                usageOf(response, System.currentTimeMillis() - startedAt));
    }

    public LLMDtos.ToolListResponse listTools() {
        return new LLMDtos.ToolListResponse(tools.size(), tools);
    }

    public void clearConversation(String conversationId) {
        chatMemory.clear(conversationId);
    }

    private LLMDtos.ModelUsage usageOf(ChatResponse response, long latencyMs) {
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        return new LLMDtos.ModelUsage(
                response.getMetadata() == null ? null : response.getMetadata().getModel(),
                response.getResult().getMetadata() == null ? null : response.getResult().getMetadata().getFinishReason(),
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getTotalTokens(),
                latencyMs);
    }
}
