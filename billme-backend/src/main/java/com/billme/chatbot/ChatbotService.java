package com.billme.chatbot;

import com.billme.chatbot.dto.ChatbotRequest;
import com.billme.chatbot.dto.ChatbotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final FAQRepository faqRepository;

    public ChatbotResponse askQuestion(ChatbotRequest request) {
        String answer = faqRepository.findAnswerByQuestion(request.getQuestion())
                .orElse("I'm sorry, I couldn't understand your question. Please contact support or try asking differently.");
        
        return new ChatbotResponse(answer);
    }
}
