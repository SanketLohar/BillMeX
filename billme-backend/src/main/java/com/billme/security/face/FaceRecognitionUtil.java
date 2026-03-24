package com.billme.security.face;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class FaceRecognitionUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static double[] parseEmbedding(Object input) {
        if (input == null) {
            throw new RuntimeException("Embedding is null");
        }

        try {
            Object effectiveInput = input;
            
            // If it's a JSON string (typical for database storage)
            if (input instanceof String str && (str.trim().startsWith("[") || str.trim().startsWith("{"))) {
                effectiveInput = objectMapper.readValue(str, Object.class);
            }

            List<Double> list;
            if (effectiveInput instanceof List<?> l) {
                // Case 1: Standard Array (handles both Number and String elements)
                list = l.stream()
                        .map(n -> Double.parseDouble(n.toString()))
                        .toList();
            } else if (effectiveInput instanceof Map<?, ?> map) {
                // Case 2: Object-format (e.g., {"0": 1.1, "1": 2.2})
                // We MUST sort keys numerically to ensure vector order
                list = map.entrySet().stream()
                        .sorted((e1, e2) -> {
                            try {
                                return Integer.compare(
                                    Integer.parseInt(e1.getKey().toString()), 
                                    Integer.parseInt(e2.getKey().toString())
                                );
                            } catch (Exception ex) {
                                return e1.getKey().toString().compareTo(e2.getKey().toString());
                            }
                        })
                        .map(e -> Double.parseDouble(e.getValue().toString()))
                        .toList();
            } else {
                throw new RuntimeException("Unsupported format after parsing: " + effectiveInput.getClass());
            }

            if (list.isEmpty()) throw new RuntimeException("Embedding list is empty");

            double[] arr = new double[list.size()];
            for (int i = 0; i < list.size(); i++) {
                arr[i] = list.get(i);
            }
            return arr;

        } catch (Exception e) {
            throw new RuntimeException("Invalid format: " + e.getMessage());
        }
    }

    public static double cosineSimilarity(double[] a, double[] b) {

        if (a.length != b.length) {
            throw new RuntimeException("Embedding dimensions mismatch");
        }

        double dot = 0.0, normA = 0.0, normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static boolean isMatch(Object storedEmbedding,
                                  double[] paymentEmbedding,
                                  double threshold) {

        double[] stored = parseEmbedding(storedEmbedding);

        double similarity = cosineSimilarity(stored, paymentEmbedding);



        return similarity >= threshold;
    }
}