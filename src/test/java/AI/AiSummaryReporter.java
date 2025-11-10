package AI;

import io.qameta.allure.Allure;
import models.MongoConnector;
import models.ReportComparator;
import org.bson.Document;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 🧠 AI Summary Reporter (Unified Allure + Global Summary)
 * --------------------------------------------------------
 * - Generates ONE unified HTML report for Allure
 * - Merges project comparison + global weighted summary + live Mongo search
 * - Includes per-project paragraph summaries for the last two executions
 */
public class AiSummaryReporter {

    private static final String projectName = System.getProperty("project.name",
            System.getenv("PROJECT_NAME") != null ? System.getenv("PROJECT_NAME") : "AI-Allure-Reuse-luffy-master");

    public static void logSummary() {
        StringBuilder finalSummary = new StringBuilder();

        try {
            // ✅ Step 1: Run comparator
            ReportComparator.compareLatestExecutions();

            var db = MongoConnector.connect();
            var collection = db.getCollection("ai_executions");

            List<Document> projectDocs = collection.find(new Document("project", projectName))
                    .into(new ArrayList<>());

            if (projectDocs.isEmpty()) {
                finalSummary.append("<div style='color:#D32F2F;font-weight:600;'>⚠️ No execution data found for project: ")
                        .append(projectName).append("</div>");
            } else {
                finalSummary.append("<h2 style='color:#1565C0;'>🧠 Execution Summary — ").append(projectName).append("</h2>");
                finalSummary.append("<hr style='border:1px solid #ccc;'>");

                Map<String, List<Document>> groupedBySubproject = projectDocs.stream()
                        .collect(Collectors.groupingBy(d -> d.getString("subproject")));

                double totalWeightedSuccess = 0.0;
                long totalEndpoints = 0;
                int countedProjects = 0;

                for (String subproject : groupedBySubproject.keySet()) {
                    List<Document> runs = groupedBySubproject.get(subproject);
                    runs.sort(Comparator.comparing(d -> d.getString("timestamp"), Comparator.reverseOrder()));

                    finalSummary.append(String.format("""
                        <div class='subproject-card'>
                            <h3>📦 %s</h3>
                    """, subproject));

                    Document subDoc = runs.get(0);
                    List<Document> allSessions = (List<Document>) subDoc.getOrDefault("sessions", List.of());

                    if (allSessions.size() < 2) {
                        finalSummary.append("<p style='color:#E65100;'>⚠️ Only one session found — comparison unavailable.</p></div>");
                        continue;
                    }

                    allSessions.sort(Comparator.comparing(s -> s.getString("createdAt"), Comparator.reverseOrder()));
                    List<Document> latestSessions = List.of(allSessions.get(0));
                    List<Document> previousSessions = List.of(allSessions.get(1));

                    // ✅ Compare 2 sessions
                    String subReport = compareSessions(
                            subproject,
                            allSessions.get(1).getString("createdAt"),
                            allSessions.get(0).getString("createdAt"),
                            previousSessions,
                            latestSessions
                    );
                    finalSummary.append(subReport).append("</div>");

                    // ✅ Weighted computation
                    long latestTotal = countEndpoints(latestSessions);
                    long latestSuccess = countSuccessEndpoints(latestSessions);
                    double latestRate = latestTotal == 0 ? 0 : (latestSuccess * 100.0 / latestTotal);

                    totalWeightedSuccess += (latestRate * latestTotal / 100.0);
                    totalEndpoints += latestTotal;
                    countedProjects++;
                }

                // ✅ Global Summary
                double avgRate = totalEndpoints > 0 ? (totalWeightedSuccess / totalEndpoints) * 100.0 : 0.0;

                String globalSummary = String.format("""
                    <div class='global-summary'>
                        <h2>🌍 Global Execution Overview</h2>
                        <p><b>📊 Overall Success Rate:</b> <span style='color:#0B8043;font-weight:700;'>%.2f%%</span><br>
                        <b>🧩 Total Subprojects:</b> %d<br>
                        <b>🧾 Total Endpoints Processed:</b> %d<br>
                        <b>🔎 Data Source:</b> MongoDB Collection <code>ai_executions</code></p>
                    </div>
                    """, avgRate, countedProjects, totalEndpoints);

                finalSummary.insert(0, globalSummary);
                finalSummary.append(String.format("""
                    <div class='summary-footer'>
                        <p><b>🧾 Summary:</b> Across <b>%d</b> subprojects, <b>%d</b> endpoints were analyzed,
                        achieving an overall success rate of <b>%.2f%%</b>. Each subproject’s latest two runs
                        were compared to measure stability, progress, and recurring issues.</p>
                    </div>
                """, countedProjects, totalEndpoints, avgRate));
            }

        } catch (Exception e) {
            finalSummary.append("<div style='color:red;font-weight:bold;'>❌ Error generating report: ")
                    .append(e.getMessage()).append("</div>");
            e.printStackTrace();
        }

        // ✅ Step 2: Stylish HTML
        String htmlReport = """
            <html>
            <head>
                <meta charset='UTF-8'>
                <style>
                    body { font-family:'Segoe UI',sans-serif;background:#f5f8fa;padding:25px;line-height:1.6; }
                    h2,h3 { color:#0D47A1;margin-bottom:8px; }
                    .subproject-card {
                        background:#ffffff;
                        border-left:6px solid #4285F4;
                        border-radius:10px;
                        padding:15px 20px;
                        margin-bottom:20px;
                        box-shadow:0 4px 10px rgba(0,0,0,0.08);
                        transition: transform 0.2s ease;
                    }
                    .subproject-card:hover { transform: translateY(-2px); }
                    .global-summary {
                        background:#E3F2FD;
                        padding:18px;
                        border-left:6px solid #1A73E8;
                        border-radius:10px;
                        margin-bottom:25px;
                    }
                    .summary-footer {
                        background:#E8EAF6;
                        padding:15px;
                        border-left:6px solid #3F51B5;
                        border-radius:10px;
                        margin-top:30px;
                        font-size:14px;
                    }
                    .ai-search {
                        background:#fff;
                        border-left:6px solid #6C63FF;
                        border-radius:10px;
                        padding:15px;
                        margin-bottom:25px;
                        box-shadow:0 4px 10px rgba(0,0,0,0.08);
                    }
                    p { margin:6px 0; }
                    code { background:#f0f3f6;padding:2px 5px;border-radius:4px; }
                    input,button { font-size:14px; }
                </style>
            </head>
            <body>
                <h2>📊 AI Subproject Analysis — %s</h2>

                <div class='ai-search'>
                    <h3>🔍 Search Historical AI Data</h3>
                    <input id='searchInput' type='text' placeholder='Search keyword (e.g., login, error, endpoint)'
                        style='padding:8px;width:60%%;border-radius:8px;border:1px solid #ccc;'>
                    <button onclick='searchAIData()'
                        style='padding:8px 15px;background-color:#0078D7;color:white;border:none;border-radius:8px;margin-left:10px;'>
                        Search
                    </button>
                    <div id='searchResults' style='margin-top:20px;'></div>
                </div>

                %s

                <script>
                async function searchAIData() {
                    const query = document.getElementById("searchInput").value.trim();
                    const resultsDiv = document.getElementById("searchResults");
                    if (!query) {
                        resultsDiv.innerHTML = "<p style='color:red;'>Please enter a search keyword.</p>";
                        return;
                    }
                    resultsDiv.innerHTML = "<p>⏳ Searching...</p>";
                    try {
                        const res = await fetch(`https://ai-allure-reuse-luffy.onrender.com/api/search?q=${encodeURIComponent(query)}`);
                        if (!res.ok) throw new Error("Server not reachable");
                        const data = await res.json();

                        if (!data.results || Object.keys(data.results).length === 0) {
                            resultsDiv.innerHTML = "<p>No results found for <b>" + query + "</b>.</p>";
                            return;
                        }

                        let html = "";
                        for (const [collection, docs] of Object.entries(data.results)) {
                            html += `<h4 style='color:#0078D7;'>📂 ${collection}</h4>`;
                            if (docs.length === 0) {
                                html += "<p><i>No entries found.</i></p>";
                            } else {
                                docs.forEach(doc => {
                                    html += `<pre style='background:#f8f9fa;padding:10px;border-radius:6px;border:1px solid #ddd;'>${JSON.stringify(doc,null,2)}</pre>`;
                                });
                            }
                        }
                        resultsDiv.innerHTML = html;
                    } catch (err) {
                        resultsDiv.innerHTML = `<p style='color:red;'>❌ Failed to fetch data: ${err.message}</p>`;
                    }
                }
                </script>
            </body>
            </html>
            """.formatted(projectName, finalSummary.toString());

        // ✅ Step 3: Attach to Allure
        Allure.addAttachment("AI Unified Report (" + projectName + ")", "text/html",
                new ByteArrayInputStream(htmlReport.getBytes(StandardCharsets.UTF_8)), "html");

        System.out.println("✅ AI Unified HTML Summary generated for project: " + projectName);
    }

    // === Helper Methods (unchanged logic) ===
    private static String compareSessions(String subproject, String prevTime, String latestTime,
                                          List<Document> prevSessions, List<Document> latestSessions) {
        long prevTotal = countEndpoints(prevSessions);
        long latestTotal = countEndpoints(latestSessions);
        long prevSuccess = countSuccessEndpoints(prevSessions);
        long latestSuccess = countSuccessEndpoints(latestSessions);
        long prevFail = prevTotal - prevSuccess;
        long latestFail = latestTotal - latestSuccess;
        double prevRate = prevTotal == 0 ? 0 : (prevSuccess * 100.0 / prevTotal);
        double latestRate = latestTotal == 0 ? 0 : (latestSuccess * 100.0 / latestTotal);
        double diff = latestRate - prevRate;

        Set<String> prevEndpoints = extractEndpoints(prevSessions);
        Set<String> latestEndpoints = extractEndpoints(latestSessions);
        Set<String> added = new HashSet<>(latestEndpoints);
        added.removeAll(prevEndpoints);
        Set<String> removed = new HashSet<>(prevEndpoints);
        removed.removeAll(latestEndpoints);
        Set<String> prevFailed = extractFailedEndpoints(prevSessions);
        Set<String> latestFailed = extractFailedEndpoints(latestSessions);
        Set<String> newFails = new HashSet<>(latestFailed);
        newFails.removeAll(prevFailed);

        StringBuilder html = new StringBuilder();
        html.append(String.format("""
                <p><b>📅 Previous Run:</b> %s — %d endpoints<br>
                <b>📅 Latest Run:</b> %s — %d endpoints</p>
                <p><b>📊 Performance:</b><br>
                • Previous Success Rate: <b>%.2f%%</b><br>
                • Latest Success Rate: <b>%.2f%%</b><br>
                • Δ Change: <b style='color:%s;'>%+.2f%%</b><br>
                • Failures: %d → %d</p>
                """, prevTime, prevTotal, latestTime, latestTotal,
                prevRate, latestRate, diff >= 0 ? "#1B5E20" : "#C62828", diff, prevFail, latestFail));

        if (!added.isEmpty()) added.forEach(ep -> html.append("➕ ").append(ep).append("<br>"));
        if (!removed.isEmpty()) removed.forEach(ep -> html.append("➖ ").append(ep).append("<br>"));
        if (!newFails.isEmpty()) newFails.forEach(ep -> html.append("❌ ").append(ep).append("<br>"));
        if (added.isEmpty() && removed.isEmpty() && newFails.isEmpty())
            html.append("✅ Stable endpoints.<br>");

        html.append(generateParagraphSummary(subproject, prevRate, latestRate, diff, prevFail, latestFail, prevTime, latestTime)).append("<br>");
        return html.toString();
    }

    private static long countEndpoints(List<Document> sessions) {
        return sessions.stream().mapToLong(s -> ((List<?>) s.getOrDefault("endpoints", List.of())).size()).sum();
    }

    private static long countSuccessEndpoints(List<Document> sessions) {
        return sessions.stream()
                .flatMap(s -> ((List<Document>) s.getOrDefault("endpoints", List.of())).stream())
                .filter(d -> {
                    int status = d.getInteger("status", 0);
                    return status >= 200 && status < 300;
                })
                .count();
    }

    private static Set<String> extractEndpoints(List<Document> sessions) {
        return sessions.stream()
                .flatMap(s -> ((List<Document>) s.getOrDefault("endpoints", List.of())).stream())
                .map(d -> d.getString("method") + " " + d.getString("endpoint"))
                .collect(Collectors.toSet());
    }

    private static Set<String> extractFailedEndpoints(List<Document> sessions) {
        return sessions.stream()
                .flatMap(s -> ((List<Document>) s.getOrDefault("endpoints", List.of())).stream())
                .filter(d -> d.getInteger("status", 0) >= 400)
                .map(d -> d.getString("method") + " " + d.getString("endpoint"))
                .collect(Collectors.toSet());
    }

    private static String generateParagraphSummary(String subproject, double prevRate, double latestRate, double delta,
                                                   long prevFails, long latestFails, String prevTime, String latestTime) {
        String trend = delta > 0 ? "improvement" : (delta < 0 ? "decline" : "no major change");
        String performance = delta > 0 ? "performed better" : (delta < 0 ? "performed slightly worse" : "maintained stability");
        String color = delta > 0 ? "#0B8043" : (delta < 0 ? "#C41E3A" : "#424242");

        return String.format("""
            <p style='color:%s;font-weight:600;font-size:13px;'>
            🧾 <b>Summary:</b> Between <b>%s</b> and <b>%s</b>, subproject <b>%s</b> showed a %s —
            from <b>%.2f%%</b> (%d fails) to <b>%.2f%%</b> (%d fails).
            It %s overall (Δ %+.2f%%).
            </p>
            """, color, prevTime, latestTime, subproject, trend, prevRate, prevFails, latestRate, latestFails, performance, delta);
    }
}
