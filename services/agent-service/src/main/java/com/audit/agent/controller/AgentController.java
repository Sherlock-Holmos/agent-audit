package main.java.com.audit.agent.controller;

import main.java.com.audit.agent.service.AgentSessionService;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final WebClient webClient;
    private final AgentSessionService agentSessionService;

    public AgentController(
        @Value("${app.data-base-url}") String dataBaseUrl,
        AgentSessionService agentSessionService
    ) {
        this.webClient = WebClient.builder().baseUrl(dataBaseUrl).build();
        this.agentSessionService = agentSessionService;
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> chat(
        @RequestBody Map<String, String> payload,
        @RequestHeader(value = "X-User-Name", required = false) String userName
    ) {
        String question = payload.getOrDefault("question", "");
        if (question.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "问题不能为空");
        }

        String currentUser = user(userName);
        if (!agentSessionService.tryAcquireQuota(currentUser)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }

        List<Map<String, Object>> history = agentSessionService.getRecentHistory(currentUser);
        String contextHint = history.isEmpty()
            ? "这是本次会话第一轮提问。"
            : "已结合最近" + history.size() + "轮会话上下文。";

        Map<String, Object> dashboard = webClient.get()
            .uri("/api/data/dashboard")
            .header("X-User-Name", currentUser)
            .retrieve()
            .bodyToMono(Map.class)
            .onErrorReturn(Map.of("completedRate", "N/A", "overdueCount", "N/A"))
            .blockOptional()
            .orElse(Map.of("completedRate", "N/A"));

        String answer = "AI建议：当前整改率为" + dashboard.get("completedRate") + "% ，"
            + "待处理疑似空值为" + dashboard.getOrDefault("overdueCount", "N/A") + "。"
            + contextHint
            + "你的问题是：" + question;

        agentSessionService.appendTurn(currentUser, question, answer);

        return Map.of(
            "question", question,
            "answer", answer,
            "confidence", 0.91,
            "historyTurns", history.size(),
            "user", currentUser
        );
    }

    private String user(String userName) {
        return (userName == null || userName.isBlank()) ? "anonymous" : userName;
    }
}
