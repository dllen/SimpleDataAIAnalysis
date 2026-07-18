package com.example.agent.controller;

import com.example.agent.model.dto.AnalysisRequest;
import com.example.agent.model.dto.AnalysisResponse;
import com.example.agent.service.AnalysisService;
import com.example.agent.exception.BusinessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/{datasetId}")
    public ResponseEntity<?> analyze(
            @PathVariable Long datasetId,
            @RequestBody AnalysisRequest request) {
        try {
            Long userId = getCurrentUserId();
            return ResponseEntity.ok(analysisService.analyze(userId, datasetId, request.question()));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(
                java.util.Map.of("error", true, "message", e.getMessage()));
        } catch (Exception e) {
            throw new BusinessException("分析失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/{datasetId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> analyzeStream(
            @PathVariable Long datasetId,
            @RequestBody AnalysisRequest request) {
        try {
            Long userId = getCurrentUserId();
            return analysisService.analyzeStream(userId, datasetId, request.question())
                    .map(data -> ServerSentEvent.<String>builder()
                            .event("message")
                            .data(data)
                            .build());
        } catch (BusinessException e) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("message")
                    .data("{\"type\":\"error\",\"data\":\"" + escapeJson(e.getMessage()) + "\"}\n")
                    .build());
        } catch (Exception e) {
            throw new BusinessException("流式分析失败: " + e.getMessage());
        }
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User user) {
            return analysisService.findUserIdByUsername(user.getUsername());
        }
        throw new com.example.agent.exception.BusinessException("未登录");
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
