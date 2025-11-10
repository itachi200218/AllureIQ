package models;

import com.mongodb.client.*;
import org.bson.Document;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 🤖 Unified AI Project-Level Comparator (Enhanced with Timestamps)
 * -----------------------------------------------------------
 * - Returns clean HTML summary text (no Allure attachment)
 * - Includes timestamps for last 2 runs per subproject
 */
public class ReportComparator {

    public static String compareLatestExecutions() {
        StringBuilder report = new StringBuilder();
        try {
            MongoDatabase db = MongoConnector.connect();
            MongoCollection<Document> collection = db.getCollection("ai_executions");
            List<Document> docs = collection.find().into(new ArrayList<>());

            if (docs.isEmpty()) {
                return "⚠️ No execution data found in MongoDB.";
            }

            Map<String, Map<String, List<Document>>> grouped =
                    docs.stream().collect(Collectors.groupingBy(
                            d -> d.getString("project") != null ? d.getString("project") : "UnknownProject",
                            Collectors.groupingBy(d -> d.getString("subproject") != null ? d.getString("subproject") : "UnknownSubproject")
                    ));

            report.append("📊 AI Subproject Comparison (AI-Allure-Reuse-luffy-master)<br><br>");

            for (String project : grouped.keySet()) {
                report.append("🧠 <b>Project:</b> ").append(project).append("<br><br>");
                Map<String, List<Document>> subprojects = grouped.get(project);
                int totalEndpoints = 0;
                double totalRate = 0.0;

                for (String sub : subprojects.keySet()) {
                    List<Document> runs = subprojects.get(sub);
                    List<Document> flattenedRuns = new ArrayList<>();

                    // 🔹 Flatten all sessions into comparable runs
                    for (Document d : runs) {
                        List<Document> sessions = (List<Document>) d.get("sessions");
                        if (sessions != null && !sessions.isEmpty()) {
                            for (Document s : sessions) {
                                Document copy = new Document();
                                copy.put("timestamp", s.getString("createdAt"));
                                copy.put("endpoints", s.get("endpoints"));
                                flattenedRuns.add(copy);
                            }
                        }
                    }

                    flattenedRuns.sort((a, b) -> b.getString("timestamp").compareTo(a.getString("timestamp")));

                    report.append("<div style='background:#fff;padding:15px;border-radius:10px;margin-bottom:10px;border-left:5px solid #0078D7;'>");
                    report.append("📦 <b>Subproject:</b> ").append(sub).append("<br>");

                    if (flattenedRuns.size() < 2) {
                        report.append("⚠️ Only one run found — no comparison.<br><br></div>");
                        continue;
                    }

                    Document latestRun = flattenedRuns.get(0);
                    Document prevRun = flattenedRuns.get(1);

                    String latestTime = latestRun.getString("timestamp");
                    String prevTime = prevRun.getString("timestamp");

                    List<Document> latest = latestRun.getList("endpoints", Document.class, new ArrayList<>());
                    List<Document> previous = prevRun.getList("endpoints", Document.class, new ArrayList<>());

                    double latestRate = calcSuccessRate(latest);
                    double prevRate = calcSuccessRate(previous);
                    double delta = latestRate - prevRate;

                    long prevFails = previous.stream().filter(d -> d.getInteger("status", 0) >= 400).count();
                    long latestFails = latest.stream().filter(d -> d.getInteger("status", 0) >= 400).count();

                    // 🕒 Add timestamps with comparison
                    report.append(String.format(
                            "🕒 <b>Previous Run:</b> %s<br>🕒 <b>Latest Run:</b> %s<br>",
                            prevTime, latestTime
                    ));

                    report.append(String.format(
                            "📅 Prev: %.2f%% (%d fails) → Latest: %.2f%% (%d fails) | Δ %.2f%%<br>",
                            prevRate, prevFails, latestRate, latestFails, delta

                    ));
// 🧾 Add paragraph summary
                    String summaryParagraph = generateParagraphSummary(sub, prevRate, latestRate, delta, prevFails, latestFails, prevTime, latestTime);
                    report.append(summaryParagraph).append("<br>");

                    compareEndpoints(previous, latest, report);
                    report.append(generateErrorSummary(previous, latest)).append("<br></div><br>");

                    totalEndpoints += latest.size();
                    totalRate += latestRate;
                }

                double avgRate = totalRate / (subprojects.size() > 0 ? subprojects.size() : 1);
                report.append(String.format(
                        "🌍 Project Avg Success Rate: %.2f%% (%d endpoints analyzed)<br><hr>",
                        avgRate, totalEndpoints
                ));
            }

        } catch (Exception e) {
            report.append("❌ Error: ").append(e.getMessage()).append("<br>");
            e.printStackTrace();
        }
        return report.toString();
    }

    private static double calcSuccessRate(List<Document> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) return 0.0;
        long success = endpoints.stream()
                .filter(d -> {
                    int status = d.getInteger("status", 0);
                    return status >= 200 && status < 300;
                }).count();
        return (success * 100.0 / endpoints.size());
    }

    private static void compareEndpoints(List<Document> prev, List<Document> latest, StringBuilder report) {
        Set<String> prevSet = prev.stream()
                .map(d -> d.getString("method") + " " + d.getString("endpoint"))
                .collect(Collectors.toSet());
        Set<String> currSet = latest.stream()
                .map(d -> d.getString("method") + " " + d.getString("endpoint"))
                .collect(Collectors.toSet());

        Set<String> added = new HashSet<>(currSet);
        added.removeAll(prevSet);
        Set<String> removed = new HashSet<>(prevSet);
        removed.removeAll(currSet);

        if (!added.isEmpty()) report.append("➕ Added: ").append(added).append("<br>");
        if (!removed.isEmpty()) report.append("➖ Removed: ").append(removed).append("<br>");

        Set<String> newFails = findNewFailures(prev, latest);
        if (!newFails.isEmpty()) report.append("❌ New Failures: ").append(newFails).append("<br>");
        else report.append("✅ Stable endpoints.<br>");
    }

    private static Set<String> findNewFailures(List<Document> prev, List<Document> latest) {
        Set<String> prevFails = getFailEndpoints(prev);
        Set<String> currFails = getFailEndpoints(latest);
        currFails.removeAll(prevFails);
        return currFails;
    }

    private static Set<String> getFailEndpoints(List<Document> endpoints) {
        return endpoints.stream()
                .filter(d -> d.getInteger("status", 0) >= 400)
                .map(d -> d.getString("method") + " " + d.getString("endpoint"))
                .collect(Collectors.toSet());
    }

    private static String generateErrorSummary(List<Document> prev, List<Document> latest) {
        Set<String> prevFails = getFailEndpoints(prev);
        Set<String> currFails = getFailEndpoints(latest);
        Set<String> recurringFails = new HashSet<>(prevFails);
        recurringFails.retainAll(currFails);
        return recurringFails.isEmpty()
                ? "🪶 Summary: No recurring errors — stability maintained."
                : "🪶 Summary: Recurring errors → " + recurringFails;
    }
    private static String generateParagraphSummary(String subproject, double prevRate, double latestRate, double delta,
                                                   long prevFails, long latestFails, String prevTime, String latestTime) {
        String trend = delta > 0 ? "improvement" : (delta < 0 ? "decline" : "no major change");
        String performance = delta > 0 ? "performed better" : (delta < 0 ? "performed slightly worse" : "maintained stability");
        String color = delta > 0 ? "#1A8E2F" : (delta < 0 ? "#C41E3A" : "#888888");

        return String.format(
                "<p style='color:%s;margin-top:5px;'>🧾 <b>Summary:</b> Between <b>%s</b> and <b>%s</b>, subproject <b>%s</b> " +
                        "showed a %s in success rate, changing from <b>%.2f%%</b> (%d failures) to <b>%.2f%%</b> (%d failures). " +
                        "Overall, it %s compared to the previous run (Δ %.2f%%).</p>",
                color, prevTime, latestTime, subproject, trend, prevRate, prevFails, latestRate, latestFails, performance, delta
        );
    }

}
