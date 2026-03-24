package com.billme.chatbot;

import com.billme.chatbot.dto.ChatbotRequest;
import com.billme.chatbot.dto.ChatbotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/ask")
    public ResponseEntity<ChatbotResponse> ask(@RequestBody ChatbotRequest request) {
        return ResponseEntity.ok(chatbotService.askQuestion(request));
    }
}
