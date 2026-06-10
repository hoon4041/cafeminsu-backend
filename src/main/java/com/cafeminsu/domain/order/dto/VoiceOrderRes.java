package com.cafeminsu.domain.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VoiceOrderRes(
        List<ParsedItem> parsedItems,
        Double confidence,
        String fallbackMessage
) {
    public record ParsedItem(
            Long menuId,
            String menuName,
            Integer quantity,
            List<Long> optionIds
    ) {
    }

    public static VoiceOrderRes failed(String message) {
        return new VoiceOrderRes(List.of(), 0.0, message);
    }
}
