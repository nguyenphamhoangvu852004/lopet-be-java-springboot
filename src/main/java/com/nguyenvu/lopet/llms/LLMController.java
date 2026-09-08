package com.nguyenvu.lopet.llms;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.llms.dto.LLMDtos;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/llms")
@RequiredArgsConstructor
public class LLMController {

    private final LLMService llmService;

    @PostMapping("/chat")
    public ApiResponse<LLMDtos.AskResponse> chat(@Valid @RequestBody LLMDtos.AskRequest request) {
        return ApiResponse.ok("Ask assistant successfully",
                llmService.ask(request.message()));
    }
}
