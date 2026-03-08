package main.java.com.audit.agent.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final WebClient webClient;

    public AgentController(@Value("${app.gateway-base-url}") String gatewayBaseUrl) {
        this.webClient = WebClient.builder().baseUrl(gatewayBaseUrl).build();
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> chat(@RequestBody Map<String, String> payload) {
        String question = payload.getOrDefault("question", "");

        Map<String, Object> dashboard = webClient.get()
            .uri("/api/data/dashboard")
            .header("Authorization", "Bearer internal-service-token")
            .retrieve()
            .bodyToMono(Map.class)
            .blockOptional()
            .orElse(Map.of("completedRate", "N/A"));

        String answer = "AI建议：当前整改率为" + dashboard.get("completedRate") + "% ，"
            + "建议优先跟进超期问题较高部门。你的问题是：" + question;

        return Map.of(
            "question", question,
            "answer", answer,
            "confidence", 0.91
        );
    }
}
