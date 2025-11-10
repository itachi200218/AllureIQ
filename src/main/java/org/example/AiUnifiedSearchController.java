package org.example;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AiUnifiedSearchController {

    @Autowired
    private MongoTemplate mongo;

    // ✅ Inject OpenRouter API Key from application.properties
    @Value("${openrouter.api.key:}")
    private String openRouterApiKey;

    // ✅ MongoDB Collections
    private static final List<String> COLLECTIONS = List.of(
            "LuffyFramework",
            "ai_context_logs",
            "ai_executions",
            "ai_hints",
            "ai_reports"
    );

    @GetMapping("/search")
    public Map<String, Object> searchAiCollections(@RequestParam("q") String query) {
        if (query == null || query.trim().isEmpty()) {
            return Map.of("error", "Search query cannot be empty");
        }

        query = query.trim();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", query);
        response.put("timestamp", new Date().toString());

        Map<String, List<Document>> resultsMap = new LinkedHashMap<>();
        StringBuilder summaryBuilder = new StringBuilder();

        // ✅ Case: Direct collection name
        if (COLLECTIONS.contains(query)) {
            List<Document> allDocs = mongo.findAll(Document.class, query);
            String finalQuery = query;
            allDocs.forEach(doc -> convertId(doc, finalQuery));
            resultsMap.put(query, allDocs);
            response.put("results", resultsMap);

            summaryBuilder.append("Summarize the key insights in collection: ")
                    .append(query)
                    .append(" based on ")
                    .append(allDocs.size())
                    .append(" records.\n\n");

            response.put("ai_summary", callOpenRouter(summaryBuilder.toString()));
            return response;
        }

        // 🔍 Fuzzy search across all collections
        for (String collectionName : COLLECTIONS) {
            long start = System.currentTimeMillis();
            List<Document> results = searchCollection(collectionName, query);
            long end = System.currentTimeMillis();

            System.out.println("✅ " + collectionName + " → " + results.size() + " results (" + (end - start) + " ms)");
            resultsMap.put(collectionName, results);

            if (!results.isEmpty()) {
                summaryBuilder.append("Collection: ").append(collectionName).append("\n");
                results.stream().limit(3).forEach(doc -> {
                    summaryBuilder.append(doc.toJson()).append("\n\n");
                });
            }
        }

        response.put("results", resultsMap);
        response.put("ai_summary", callOpenRouter(summaryBuilder.toString()));
        return response;
    }

    private List<Document> searchCollection(String collectionName, String query) {
        List<Document> matches = new ArrayList<>();
        try {
            Pattern pattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            List<Document> allDocs = mongo.find(new Query().limit(200), Document.class, collectionName);

            for (Document doc : allDocs) {
                for (Map.Entry<String, Object> entry : doc.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof String && pattern.matcher((String) value).find()) {
                        convertId(doc, collectionName);
                        matches.add(doc);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error searching in " + collectionName + ": " + e.getMessage());
        }
        return matches;
    }

    private void convertId(Document doc, String collectionName) {
        Object id = doc.get("_id");
        if (id instanceof ObjectId) {
            doc.put("_id", ((ObjectId) id).toHexString());
        }
        doc.put("_collection", collectionName);
    }

    // ✅ AI summarization via OpenRouter
    private String callOpenRouter(String text) {
        try {
            String apiKey = openRouterApiKey;
            if (apiKey == null || apiKey.isEmpty()) {
                return "⚠️ AI summarization skipped: Missing OPENROUTER_API_KEY";
            }

            URL url = new URL("https://openrouter.ai/api/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);

            // ⚙️ Medium-length summary instruction
            String systemPrompt = """
            You are an expert AI that summarizes MongoDB test data.
            Write a concise yet meaningful overview covering:
            - What the data seems to represent (AI logs, reports, etc.)
            - Key fields and their typical values
            - Patterns or repeated structures in JSON
            - Any relationships between collections
            Keep it short, structured, and clear — around 4–6 bullet points or small paragraphs.
        """;

            JSONObject payload = new JSONObject();
            payload.put("model", "gpt-4o-mini");
            payload.put("messages", List.of(
                    new JSONObject().put("role", "system").put("content", systemPrompt),
                    new JSONObject().put("role", "user").put("content", "Summarize this MongoDB data:\n" + text)
            ));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes());
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }

            JSONObject json = new JSONObject(result.toString());
            return json
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

        } catch (Exception e) {
            return "⚠️ AI summarization failed: " + e.getMessage();
        }
    }

    // ✅ NEW: Summary Endpoint for Homepage Summary Section
    @GetMapping("/summary")
    public Map<String, Object> getAiSummary() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", new Date().toString());

        try {
            // Fetch a few reports from ai_reports collection
            List<Document> reports = mongo.find(new Query().limit(10), Document.class, "ai_reports");

            if (reports.isEmpty()) {
                response.put("ai_summary", "⚠️ No AI summary data found in database.");
                return response;
            }

            // Combine summaries or content
            StringBuilder summaryBuilder = new StringBuilder("Summarize the following AI reports and test logs:\n\n");
            for (Document report : reports) {
                if (report.containsKey("ai_summary")) {
                    summaryBuilder.append(report.get("ai_summary")).append("\n\n");
                } else {
                    summaryBuilder.append(report.toJson()).append("\n\n");
                }
            }

            // Call OpenRouter for summarization
            String aiSummary = callOpenRouter(summaryBuilder.toString());
            response.put("ai_summary", aiSummary);

        } catch (Exception e) {
            response.put("error", "❌ Failed to fetch AI summary: " + e.getMessage());
        }

        return response;
    }
}
