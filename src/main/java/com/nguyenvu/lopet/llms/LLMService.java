package com.nguyenvu.lopet.llms;

import com.anthropic.models.messages.OutputConfig;
import com.nguyenvu.lopet.common.exception.ServiceUnavailableException;
import com.nguyenvu.lopet.llms.dto.LLMDtos;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import static org.springframework.ai.anthropic.AnthropicChatOptions.builder;

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

    private final ChatClient chatClientWithTools;

    public LLMService(ChatClient.Builder builder,
                      AccountTool accountTool,
                      StatisticTool statisticTool,
                      AnthropicChatModel anthropicChatModel) {
        this.chatClientWithTools = builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(accountTool, statisticTool)
                .defaultOptions(builder()
                        .effort(OutputConfig.Effort.MEDIUM)
                )
                .build();
    }

    public LLMDtos.AskResponse ask(String message) {

        long startedAt = System.currentTimeMillis();
        ChatResponse response = chatClientWithTools.prompt()
                .user(message)
                .call()
                .chatResponse();

        if (response == null || response.getResult() == null) {
            throw new ServiceUnavailableException("The assistant returned no content");
        }

        return new LLMDtos.AskResponse(response.getResult().getOutput().getText(),
                usageOf(response, System.currentTimeMillis() - startedAt));
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
