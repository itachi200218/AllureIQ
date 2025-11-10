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

    @Value("${openrouter.api.key:}")
    private String openRouterApiKey;

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

        // ✅ Direct collection name case
        if (COLLECTIONS.contains(query)) {
            List<Document> allDocs = mongo.findAll(Document.class, query);
            String finalQuery = query;
            allDocs.forEach(doc -> {
                convertId(doc, finalQuery);
                sanitizePaths(doc);
            });
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
            List<Document> allDocs = mongo.find(new Query().limit(60), Document.class, collectionName); // ✅ Only first 60

            for (Document doc : allDocs) {
                if (containsMatch(doc, pattern)) {
                    convertId(doc, collectionName);
                    sanitizePaths(doc); // ✅ Neutralize redirect-like fields
                    matches.add(doc);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error searching in " + collectionName + ": " + e.getMessage());
        }
        return matches;
    }
    private boolean containsMatch(Object value, Pattern pattern) {
        if (value == null) return false;

        if (value instanceof String) {
            return pattern.matcher((String) value).find();
        } else if (value instanceof Document doc) {
            for (Object v : doc.values()) if (containsMatch(v, pattern)) return true;
        } else if (value instanceof Map<?, ?> map) {
            for (Object v : map.values()) if (containsMatch(v, pattern)) return true;
        } else if (value instanceof List<?> list) {
            for (Object item : list) if (containsMatch(item, pattern)) return true;
        }

        return false;
    }

    private void convertId(Document doc, String collectionName) {
        Object id = doc.get("_id");
        if (id instanceof ObjectId) {
            doc.put("_id", ((ObjectId) id).toHexString());
        }
        doc.put("_collection", collectionName);
    }

//    private void sanitizePaths(Document doc) {
//        for (Map.Entry<String, Object> entry : doc.entrySet()) {
//            Object value = entry.getValue();
//
//            if (value instanceof String str && str.startsWith("/")) {
//                doc.put(entry.getKey(), "PATH:" + str);
//            } else if (value instanceof Document nested) {
//                sanitizePaths(nested);
//            } else if (value instanceof List<?> list) {
//                for (Object item : list) {
//                    if (item instanceof Document d) sanitizePaths(d);
//                }
//            }
//        }
//    }

    private String callOpenRouter(String text) {
        try {
            if (openRouterApiKey == null || openRouterApiKey.isEmpty()) {
                return "⚠️ AI summarization skipped: Missing OPENROUTER_API_KEY";
            }

            URL url = new URL("https://openrouter.ai/api/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + openRouterApiKey);
            conn.setDoOutput(true);

            String systemPrompt = """
            You are an expert AI that summarizes MongoDB testing and automation data.
            Provide a professional, structured summary highlighting:
            - Data purpose and structure
            - Key fields and values
            - Any notable patterns or relationships between collections
            - Insights relevant for QA or AI analysis
            Keep it clean and formatted with 4–6 short bullet points.
            """;

            JSONObject payload = new JSONObject();
            payload.put("model", "gpt-4o-mini");
            payload.put("messages", List.of(
                    new JSONObject().put("role", "system").put("content", systemPrompt),
                    new JSONObject().put("role", "user").put("content", "Analyze this MongoDB dataset:\n" + text)
            ));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes());
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) result.append(line);

            JSONObject json = new JSONObject(result.toString());
            return json.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content");

        } catch (Exception e) {
            return "⚠️ AI summarization failed: " + e.getMessage();
        }
    }
    // ✅ Place this anywhere in the class (outside other methods)
    private void sanitizePaths(Document doc) {
        for (String key : doc.keySet()) {
            Object value = doc.get(key);
            if (value instanceof String) {
                String strValue = (String) value;
                // 🧱 Detect and neutralize redirect-like or localhost URLs
                if (strValue.matches("(?i).*localhost.*|.*http://.*|.*https://.*|.*login.*|.*redirect.*")) {
                    doc.put(key, "[redacted or internal link]");
                }
            } else if (value instanceof Document) {
                sanitizePaths((Document) value); // recursive sanitize
            } else if (value instanceof List<?>) {
                List<?> list = (List<?>) value;
                for (Object item : list) {
                    if (item instanceof Document) {
                        sanitizePaths((Document) item);
                    }
                }
            }
        }
    }


    @GetMapping("/summary")
    public Map<String, Object> getAiSummary() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", new Date().toString());

        try {
            List<Document> reports = mongo.find(new Query().limit(10), Document.class, "ai_reports");

            if (reports.isEmpty()) {
                response.put("ai_summary", "⚠️ No AI summary data found in database.");
                return response;
            }

            StringBuilder summaryBuilder = new StringBuilder("Summarize the following AI reports and test logs:\n\n");
            for (Document report : reports) {
                if (report.containsKey("ai_summary"))
                    summaryBuilder.append(report.get("ai_summary")).append("\n\n");
                else summaryBuilder.append(report.toJson()).append("\n\n");
            }

            response.put("ai_summary", callOpenRouter(summaryBuilder.toString()));
        } catch (Exception e) {
            response.put("error", "❌ Failed to fetch AI summary: " + e.getMessage());
        }

        return response;
    }
}
