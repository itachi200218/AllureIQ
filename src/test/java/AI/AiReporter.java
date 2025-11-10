package AI;

import io.qameta.allure.Allure;
import models.MongoConnector;
import models.ReportComparator;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 💡 Advanced AI Reporter
 * - Captures endpoints, status codes, and errors
 * - Summarizes all test results via GeminiAI
 * - Generates styled HTML + MongoDB + Allure integration
 * - Now includes run-to-run comparison summary (success %, failures, repeated errors)
 */
public class AiReporter {
    private static final List<String> records = new ArrayList<>();
    private static final List<String> errorRecords = new ArrayList<>();
    private static final Map<String, Integer> endpointStatusMap = new LinkedHashMap<>();

    // ✅ Log generic record
    public static synchronized void addRecord(String rec) {
        String entry = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + rec;
        records.add(entry);
    }

    public static synchronized void log(String msg) {
        addRecord("INFO: " + msg);
    }

    // ✅ Log endpoint with status
    public static synchronized void logEndpoint(String endpoint, int statusCode) {
        addRecord("ENDPOINT: " + endpoint + " | STATUS: " + statusCode);
        endpointStatusMap.put(endpoint, statusCode);
    }

    // ✅ Log error with context
    public static synchronized void logError(String endpoint, String errorMessage) {
        String formatted = "ERROR: " + endpoint + " | Message: " + errorMessage;
        addRecord(formatted);
        errorRecords.add(formatted);
    }

    // ✅ Generate full summary (AI + HTML + Mongo + Allure)
    public static synchronized String generateAndSaveSummary() {
        if (records.isEmpty()) return "⚠️ No records found for this run.";

        // Build combined logs
        StringBuilder sb = new StringBuilder();
        for (String r : records) sb.append(r).append("\n");
// ✅ Count unique (method + endpoint) pairs to prevent AI overcount
        Set<String> uniqueEndpoints = new LinkedHashSet<>(endpointStatusMap.keySet());
        int totalEndpoints = uniqueEndpoints.size();
        int successCount = (int) endpointStatusMap.values().stream()
                .filter(s -> s >= 200 && s < 300)
                .count();

        // Generate error summary
        String errorSection = errorRecords.isEmpty()
                ? "No critical errors encountered."
                : String.join("\n", errorRecords);

        // Build AI prompt
        String prompt = """
You are an expert QA Automation Analyst and SDET.
Review the following API test logs and generate a **medium-detailed analysis report**.
Keep it structured, concise, and technically insightful.

🧾 Structure:
1️⃣ Overall Summary:
   - Give a 3–4 line overview covering **total unique APIs tested** (avoid counting duplicates), success rate, and general performance.
   - Mention if authentication, CRUD, or integration endpoints were included.

2️⃣ Key Issues:
   - Use short, direct bullet points.
   - Summarize main failures (4xx or 5xx codes), with brief context.

3️⃣ Technical Root Cause Insights:
   - For each issue, provide the likely cause (e.g., invalid input, missing headers, database mismatch, backend exception).
   - Keep explanations one line each, technical but clear.

4️⃣ Suggestions:
   - Give practical improvement points for both code and API testing strategy.
   - Include examples like validation checks, better payload handling, or retry logic.

5️⃣ Endpoints Tested:
   - List **only unique endpoints** with their method, status code, and short response summary.
   - Keep each endpoint on its own line.

6️⃣ Error Breakdown:
   - List only endpoints with non-200 status codes or exceptions.
   - Include cause or likely failure reason in short, readable bullets.

🧩 Important Notes:
   - If the same endpoint appears multiple times, count it **only once** in totals.
   - Summarize smartly and avoid inflating counts due to repeated requests.
   - Maintain clean Markdown formatting.

Keep the tone professional, readable, and medium in length (neither too short nor verbose).
Avoid repeating data already presented.
Use readable icons and Markdown-style bullets for clarity.

🧪 Logs:
%s
""".formatted(sb);

        // Send prompt to GeminiAI
        String aiResponse = GeminiAI.generate(prompt);

        // Extract AI sections
        String summaryBox = extract(aiResponse, "Overall Summary");
        String issuesBox = extract(aiResponse, "Key Issues");
        String rootCauseBox = extract(aiResponse, "Technical Root Cause Insights");
        String suggestionsBox = extract(aiResponse, "Suggestions");
        String endpointsBox = extract(aiResponse, "Endpoints Tested");
        String errorsBox = extract(aiResponse, "Error Breakdown");

        // Build timestamped filename
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String fileName = "ai_summary_" + timestamp + ".html";

        // --- NEW: Comparison summary (current vs previous run only) ---
        String comparisonSummary = buildComparisonSummary(sb.toString());

        // ✅ HTML Report
        String htmlReport = """
        <html>
        <head>
        <meta charset='UTF-8'>
        <style>
            body { font-family: 'Segoe UI', sans-serif; background:#f5f7fa; padding:25px; }
            h2 { color:#0078D7; margin-bottom:5px; }
            h3 { color:#333; }
            .timestamp { color:#666; font-size:14px; margin-bottom:12px; }
            .card {
                background:#fff; border-radius:12px; padding:20px; margin-bottom:20px;
                box-shadow:0 4px 10px rgba(0,0,0,0.08);
            }
            .summary { border-left:6px solid #0078D7; }
            .issues { border-left:6px solid #DC3545; }
            .rootcause { border-left:6px solid #FFC107; }
            .suggestions { border-left:6px solid #28A745; }
            .endpoints { border-left:6px solid #6C63FF; }
            .errors { border-left:6px solid #FF5733; }
            ul { padding-left:20px; }
            li { margin:6px 0; line-height:1.6; }
            .endpoint-list li::before { content: "🌐 "; }
            .issue-list li::before { content: "⚠️ "; }
            .suggestion-list li::before { content: "💡 "; }
            .rootcause-list li::before { content: "🧠 "; }
            .error-list li::before { content: "❌ "; }
            details { margin-bottom:15px; }
            summary { cursor:pointer; font-weight:600; }
            .comparison { color:#222; font-size:15px; margin-bottom:12px; line-height:1.5; }
        </style>
        </head>
        <body>
            <h2>🤖 AI Test Intelligence Report — Unified View</h2>
            <div class='timestamp'>🕒 Generated: %s</div>

            <div class='card summary'>
                <h3>🧾 Overall Summary</h3>
                <p>%s</p>
            </div>

            <div class='card issues'>
                <h3>⚠️ Key Issues</h3>
                <ul class='issue-list'>%s</ul>
            </div>

            <div class='card rootcause'>
                <h3>🧠 Technical Root Cause Insights</h3>
                <ul class='rootcause-list'>%s</ul>
            </div>

            <div class='card suggestions'>
                <h3>💡 Suggestions</h3>
                <ul class='suggestion-list'>%s</ul>
            </div>

            <div class='card endpoints'>
                <h3>🌐 Endpoints Tested</h3>
                <ul class='endpoint-list'>%s</ul>
            </div>

            <div class='card errors'>
                <h3>❌ Error Breakdown</h3>
                <details open>
                    <summary>Click to view error logs</summary>
                    <pre>%s</pre>
                </details>
            </div>

            <hr style='margin-top:30px;'>
            <p style='color:#555;'>✅ AI summary generated and stored successfully.</p>
        </body>
        </html>
    """.formatted(
                LocalDateTime.now(),      // 1 → Generated timestamp
                summaryBox,
                comparisonSummary,  // 2 → AI Overall Summary// 3 → New Comparison Summary (detailed)
                toBulletList(issuesBox),  // 4 → Issues
                toBulletList(rootCauseBox), // 5 → Root Causes
                toBulletList(suggestionsBox), // 6 → Suggestions
                toBulletList(endpointsBox)  // 7 → Endpoints
        );

        // Save locally
        try (PrintWriter out = new PrintWriter(new FileWriter(fileName, false))) {
            out.println(htmlReport);
        } catch (Exception e) {
            aiResponse += "\n⚠️ File save failed: " + e.getMessage();
        }

        // Add to Allure
        Allure.addAttachment("AI Unified Report", "text/html", htmlReport, ".html");

        // Save to Mongo
        MongoConnector.saveReport("AI Test Intelligence", aiResponse, sb.toString());
        System.out.println("✅ Report saved successfully to MongoDB.");
// 🔁 Run post-summary AI Execution Comparison
        System.out.println("\n📊 Launching AI Execution Comparison Report...\n");
        ReportComparator.compareLatestExecutions();



        // Clear all data
        records.clear();
        errorRecords.clear();
        endpointStatusMap.clear();

        System.out.println("🧹 AI Reporter cleared after summary generation.");
        return aiResponse;
    }


    // ----------------------------- Comparison helpers --------------------------------

    /**
     * Uses current endpointStatusMap for current run, and reads previous records via MongoConnector.getLastReports(2).
     */
    private static String buildComparisonSummary(String currentRecords) {
        try {
            // ✅ Fetch last 2 runs from ai_executions collection (real execution logs)
            List<org.bson.Document> lastExecutions = models.MongoConnector.getRecentEndpoints(100);

            if (lastExecutions.isEmpty()) {
                return "📊 No API executions found in MongoDB (ai_executions).";
            }

            // Split into previous and current by timestamp
            // (assuming last executions are sorted latest → oldest)
            Map<String, Integer> currentMap = new LinkedHashMap<>();
            Map<String, Integer> previousMap = new LinkedHashMap<>();

            long latestTimestamp = lastExecutions.get(0).getLong("timestamp");
            long timeGap = 2 * 60 * 1000; // 2-minute window separation (adjust as per your test run gap)

            for (org.bson.Document doc : lastExecutions) {
                String endpoint = doc.getString("endpoint");
                String method = doc.getString("method");
                int status = doc.getInteger("status", 0);
                long ts = doc.containsKey("timestamp") ? doc.getLong("timestamp") : 0;
                String key = method + " " + endpoint;

                if (latestTimestamp - ts < timeGap) {
                    currentMap.put(key, status);
                } else {
                    previousMap.put(key, status);
                }
            }

            if (currentMap.isEmpty()) {
                return "📊 Current execution data not found in ai_executions.";
            }

            int currTotal = currentMap.size();
            int currSuccess = (int) currentMap.values().stream().filter(s -> s >= 200 && s < 300).count();
            int currFail = currTotal - currSuccess;
            double currRate = currTotal > 0 ? (currSuccess * 100.0 / currTotal) : 0.0;

            int prevTotal = previousMap.size();
            int prevSuccess = (int) previousMap.values().stream().filter(s -> s >= 200 && s < 300).count();
            int prevFail = prevTotal - prevSuccess;
            double prevRate = prevTotal > 0 ? (prevSuccess * 100.0 / prevTotal) : 0.0;

            double diff = currRate - prevRate;

            // Identify repeated or new failed endpoints
            Set<String> prevFailed = new LinkedHashSet<>();
            previousMap.forEach((ep, st) -> {
                if (st < 200 || st >= 300) prevFailed.add(ep);
            });

            Set<String> currFailed = new LinkedHashSet<>();
            currentMap.forEach((ep, st) -> {
                if (st < 200 || st >= 300) currFailed.add(ep);
            });

            Set<String> repeated = new LinkedHashSet<>(prevFailed);
            repeated.retainAll(currFailed);

            // 🧩 Format messages
            String repeatedMsg = repeated.isEmpty()
                    ? (currFailed.isEmpty()
                    ? "✅ No failing endpoints in current run."
                    : "⚠️ New failing endpoints: " + String.join(", ", currFailed))
                    : "🔁 Repeated failing endpoints: " + String.join(", ", repeated);

            String fixedMsg = prevFailed.stream()
                    .filter(ep -> !currFailed.contains(ep))
                    .reduce("", (acc, ep) -> acc + "✅ Fixed endpoint: " + ep + "<br>", String::concat);

            return String.format("""
            📊 <b>Real Execution Comparison (ai_executions)</b><br>
            • Previous Success Rate: %.2f%% (%d/%d)<br>
            • Current Success Rate: %.2f%% (%d/%d)<br>
            • Difference: %+.2f%%<br>
            • Failures Change: %d → %d<br>
            %s<br>
            %s
            """,
                    prevRate, prevSuccess, prevTotal,
                    currRate, currSuccess, currTotal,
                    diff, prevFail, currFail, repeatedMsg, fixedMsg
            );

        } catch (Exception e) {
            return "⚠️ Comparison unavailable (ai_executions): " + e.getMessage();
        }
    }


    // Count successes in current run using endpointStatusMap
    private static int countSuccessCurrent() {
        int count = 0;
        for (Integer status : endpointStatusMap.values()) {
            if (status != null && status >= 200 && status < 300) count++;
        }
        return count;
    }

    // Parse total endpoints lines from previous run's records (lines that start with "ENDPOINT:")
    private static int countTotalEndpointsFromRecords(String records) {
        if (records == null || records.isBlank()) return 0;
        int total = 0;
        String[] lines = records.split("\\r?\\n");
        for (String line : lines) {
            if (line.trim().startsWith("ENDPOINT:")) total++;
        }
        return total;
    }

    // Parse successes (STATUS: 200-299) from previous run's records
    private static int countSuccessFromRecords(String records) {
        if (records == null || records.isBlank()) return 0;
        int count = 0;
        String[] lines = records.split("\\r?\\n");
        for (String line : lines) {
            if (line.trim().startsWith("ENDPOINT:")) {
                // line format: ENDPOINT: /path | STATUS: 200
                if (line.contains("STATUS:")) {
                    String afterStatus = line.substring(line.indexOf("STATUS:") + "STATUS:".length()).trim();
                    try {
                        String statusToken = afterStatus.split("\\s+")[0].replaceAll("[^0-9]", "");
                        int st = Integer.parseInt(statusToken);
                        if (st >= 200 && st < 300) count++;
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return count;
    }

    // Return set of failed endpoints from previous records (non-2xx statuses)
    private static Set<String> failedEndpointsFromRecords(String records) {
        Set<String> failed = new LinkedHashSet<>();
        if (records == null || records.isBlank()) return failed;
        String[] lines = records.split("\\r?\\n");
        for (String line : lines) {
            if (line.trim().startsWith("ENDPOINT:")) {
                // extract endpoint and status
                try {
                    String[] parts = line.split("\\|");
                    String epPart = parts[0].trim(); // "ENDPOINT: /path"
                    String endpoint = epPart.substring(epPart.indexOf("ENDPOINT:") + "ENDPOINT:".length()).trim();
                    int status = 0;
                    for (String p : parts) {
                        if (p.contains("STATUS:")) {
                            String afterStatus = p.substring(p.indexOf("STATUS:") + "STATUS:".length()).trim();
                            String statusToken = afterStatus.split("\\s+")[0].replaceAll("[^0-9]", "");
                            status = Integer.parseInt(statusToken);
                        }
                    }
                    if (!(status >= 200 && status < 300)) {
                        failed.add(endpoint);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return failed;
    }

    // Return set of failed endpoints from current run's endpointStatusMap
    private static Set<String> failedEndpointsFromMap() {
        Set<String> failed = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> e : endpointStatusMap.entrySet()) {
            Integer st = e.getValue();
            if (st == null || st < 200 || st >= 300) {
                failed.add(e.getKey());
            }
        }
        return failed;
    }

    // ----------------------------- Existing helpers --------------------------------

    // 🧩 Extract section text
    private static String extract(String response, String header) {
        try {
            String[] parts = response.split("(?i)" + header + "[:：]");
            if (parts.length < 2) return "";
            String next = parts[1];
            String[] nextSplit = next.split("\\n\\s*\\n");
            return nextSplit[0].replace("*", "").replace("•", "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    // 🧩 Convert to bullet list
    private static String toBulletList(String text) {
        if (text == null || text.isBlank()) return "<li><i>No data</i></li>";
        String[] lines = text.split("\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (!line.trim().isEmpty()) sb.append("<li>").append(line.trim()).append("</li>");
        }
        return sb.toString();
    }

    // ✅ For debugging / plain summary
    public static synchronized String generateEndpointSummary() {
        StringBuilder summary = new StringBuilder("🌐 Endpoints Summary\n");
        endpointStatusMap.forEach((ep, status) ->
                summary.append("- ").append(ep).append(" → ").append(status).append("\n"));
        if (summary.length() == "🌐 Endpoints Summary\n".length())
            summary.append("No endpoint data recorded.\n");
        return summary.toString();
    }

    public static synchronized void clear() {
        records.clear();
        errorRecords.clear();
        endpointStatusMap.clear();
    }

}
