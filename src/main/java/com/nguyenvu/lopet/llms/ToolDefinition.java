package com.nguyenvu.lopet.llms;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record ToolDefinition(
        String name,
        String description,
        @JsonProperty("input_schema") InputSchema inputSchema
) {

    public static ToolDefinition getTotalAmountValidAccount() {
        ToolDefinition toolDefinition = new ToolDefinition(
                "get_total_amount_valid_account",
                "Look up how many valid accounts currently exist in the system. Takes no parameter; it resolves from the current session automatically.",
                new InputSchema(
                        "object",
                        Map.of(),
                        List.of()
                )
        );
        return toolDefinition;
    }

    record InputSchema(
            String type,
            Map<String, PropertyDefinition> properties,
            List<String> required
    ) {
    }

    record PropertyDefinition(
            String type,
            String description
    ) {
    }
}
