package com.billme.chatbot;

import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class FAQRepository {

    private final Map<String, String> faqs = new HashMap<>();

    public FAQRepository() {
        faqs.put("how do i create an invoice?", "Go to Merchant Dashboard -> Create Invoice -> Add products.");
        faqs.put("how do i register?", "Click on the Register button on the top right, select your role (Merchant or Customer), and fill in the details.");
        faqs.put("what is the platform fee?", "The platform charges a 2% processing fee on top of the subtotal.");
        faqs.put("how do i add a product?", "Go to Merchant Dashboard -> Products -> Add Product.");
        faqs.put("how to pay an invoice?", "Click on the payment link in the email or use FacePay / UPI on the dashboard.");
    }

    public Optional<String> findAnswerByQuestion(String question) {
        if (question == null) return Optional.empty();
        
        String cleanQuestion = question.trim().toLowerCase();
        
        for (Map.Entry<String, String> entry : faqs.entrySet()) {
            if (cleanQuestion.contains(entry.getKey().replace("?", ""))) {
                return Optional.of(entry.getValue());
            }
        }
        
        return Optional.empty();
    }
}
