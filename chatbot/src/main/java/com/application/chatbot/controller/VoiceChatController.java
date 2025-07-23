package com.application.chatbot.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class VoiceChatController {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${weather.api.key}")
    private String weatherApiKey;

    @Value("${llm.endpoint}")
    private String llmEndpoint;

    @Value("${llm.model.name}")
    private String modelName;

    @PostMapping("/voice-chat")
    public ResponseEntity<String> handleVoice(@RequestParam("audio") MultipartFile audio) throws IOException {
        // 1. Transcribe audio via Whisper API
        String text = transcribeAudio(audio);
        if (text == null) return ResponseEntity.status(500).body("Failed to transcribe audio");

        // 2. Determine intent
        String intent = detectIntent(text);

        // 3. Handle based on intent
        String response;
        switch (intent) {
            case "weather":
                response = fetchWeather("Bangalore");
                break;
            case "time":
                response = java.time.LocalTime.now().toString();
                break;
            case "greeting":
                response = "Hi there! How can I assist you today?";
                break;
            default:
                response = callLLM(text);
        }

        return ResponseEntity.ok("You said: \"" + text + "\"\n\n🧠 Response: " + response);
    }

    private String transcribeAudio(MultipartFile audio) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource fileRes = new ByteArrayResource(audio.getBytes()) {
            @Override public String getFilename() {
                return audio.getOriginalFilename();
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("audio", fileRes);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response =
            restTemplate.postForEntity("http://localhost:5005/transcribe", request, Map.class);

        return (String) response.getBody().get("text");
    }

    private String detectIntent(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("weather")) return "weather";
        if (lower.contains("time")) return "time";
        if (lower.contains("hello") || lower.contains("hi")) return "greeting";
        return "general";
    }

    private String fetchWeather(String city) {
        String url = String.format(
            "https://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s&units=metric",
            city, weatherApiKey);

        try {
            ResponseEntity<Map> weatherRes = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> main = (Map<String, Object>) weatherRes.getBody().get("main");
            Map<String, Object> weather = ((java.util.List<Map<String, Object>>) weatherRes.getBody().get("weather")).get(0);

            return String.format("📍 %s weather: %s, %.1f°C",
                city,
                weather.get("description"),
                main.get("temp"));
        } catch (Exception e) {
            return "Unable to fetch weather now.";
        }
    }

    private String callLLM(String prompt) {
        String payload = String.format("{\"model\": \"%s\", \"prompt\": \"%s\", \"stream\": false}", modelName, prompt);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(payload, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(llmEndpoint, request, Map.class);
        return (String) response.getBody().get("response");
    }
}