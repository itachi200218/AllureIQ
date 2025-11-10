package org.example;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AiUnifiedSearchController {

    @Autowired
    private MongoTemplate mongo;

    // All MongoDB collections to search
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

        // ✅ NEW: If query exactly matches a collection name, return all data from that collection
        if (COLLECTIONS.contains(query)) {
            System.out.println("📂 Fetching all documents from: " + query);
            List<Document> allDocs = mongo.findAll(Document.class, query);

            // Convert ObjectIds to strings
            for (Document doc : allDocs) {
                Object id = doc.get("_id");
                if (id instanceof ObjectId) {
                    doc.put("_id", ((ObjectId) id).toHexString());
                }
                doc.put("_collection", query);
            }

            resultsMap.put(query, allDocs);
            response.put("results", resultsMap);
            return response;
        }

        // 🔍 Normal fuzzy search for all collections
        for (String collectionName : COLLECTIONS) {
            long start = System.currentTimeMillis();
            List<Document> results = searchCollection(collectionName, query);
            long end = System.currentTimeMillis();

            System.out.println("✅ " + collectionName + " → " + results.size() + " results (" + (end - start) + " ms)");
            resultsMap.put(collectionName, results);
        }

        response.put("results", resultsMap);
        return response;
    }

    private List<Document> searchCollection(String collectionName, String query) {
        List<Document> matches = new ArrayList<>();
        try {
            // Search text case-insensitive
            Pattern pattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

            // Fetch only 200 docs for safety (avoid hangs)
            List<Document> allDocs = mongo.find(new Query().limit(200), Document.class, collectionName);

            for (Document doc : allDocs) {
                for (Map.Entry<String, Object> entry : doc.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof String && pattern.matcher((String) value).find()) {
                        // Convert ObjectId to string
                        Object id = doc.get("_id");
                        if (id instanceof ObjectId) {
                            doc.put("_id", ((ObjectId) id).toHexString());
                        }

                        doc.put("_collection", collectionName);
                        matches.add(doc);
                        break; // move to next doc after first match
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Error searching in " + collectionName + ": " + e.getMessage());
        }
        return matches;
    }
}
