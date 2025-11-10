package org;

import AI.AiReporter;
import AI.AiTestDataGenerator;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Epic("AI + API Automation Suite")
@Feature("Job Tracker API Testing with Gemini AI + Allure Report")
public class TrackerTest {

    private static Map<String, String> header;
    private static String jobId1;
    private static String jobId2;

    private static String BASE_URI;
    private static String GET_JOBS;
    private static String POST_JOB;
    private static String PUT_JOB;
    private static String DELETE_JOB;

    private static String CREATE_METHOD;
    private static String READ_METHOD;
    private static String UPDATE_METHOD;
    private static String DELETE_METHOD;

    @BeforeClass
    @Step("Initialize API Base URI and common headers from Tracker.env")
    public void setup() {
        System.setProperty("projectName", "TrackerTest");

        // 🔍 Load from Tracker.env
        String rootPath = new File(System.getProperty("user.dir")).getAbsolutePath();
        String trackerEnvPath = rootPath + File.separator + "ENV" + File.separator + "Tracker.env";
        System.out.println("🔍 Loading environment file: " + trackerEnvPath);

        io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.configure()
                .filename("Tracker.env")
                .directory(rootPath + File.separator + "ENV")
                .ignoreIfMissing()
                .load();

        // 🌐 Base URLs
        BASE_URI = dotenv.get("BASE_URL");
        GET_JOBS = dotenv.get("GET_ALL_JOBS_ENDPOINT");
        POST_JOB = dotenv.get("CREATE_JOB_ENDPOINT");
        PUT_JOB = dotenv.get("UPDATE_JOB_ENDPOINT");
        DELETE_JOB = dotenv.get("DELETE_JOB_ENDPOINT");

        // 🔧 Methods
        CREATE_METHOD = dotenv.get("CREATE_JOB_METHOD", "POST");
        READ_METHOD = dotenv.get("GET_ALL_JOBS_METHOD", "GET");
        UPDATE_METHOD = dotenv.get("UPDATE_JOB_METHOD", "PUT");
        DELETE_METHOD = dotenv.get("DELETE_JOB_METHOD", "DELETE");

        // ✅ Apply base URI for all API calls
        ApiReuse.uri(BASE_URI);

        // ✅ Set default headers
        header = new HashMap<>();
        header.put("Content-Type", "application/json");

        // ✅ Log loaded environment
        System.out.println("\n✅ Tracker.env Loaded Successfully:");
        System.out.println("BASE_URI = " + BASE_URI);
        System.out.println("GET_JOBS = " + GET_JOBS);
        System.out.println("POST_JOB = " + POST_JOB);
        System.out.println("PUT_JOB = " + PUT_JOB);
        System.out.println("DELETE_JOB = " + DELETE_JOB);
        System.out.println("CREATE_METHOD = " + CREATE_METHOD);
        System.out.println("READ_METHOD = " + READ_METHOD);
        System.out.println("UPDATE_METHOD = " + UPDATE_METHOD);
        System.out.println("DELETE_METHOD = " + DELETE_METHOD + "\n");
    }

    // ---------------- GET ----------------
    @Test(priority = 1, description = "Fetch existing job data (GET /api/jobs)")
    @Story("Read Operation")
    @Severity(SeverityLevel.NORMAL)
    public void testGetJobs() {
        Allure.step("Fetching all job data from " + GET_JOBS);

        Response getRes = ApiReuse.execute(new ApiReuse(GET_JOBS, READ_METHOD, null), header);

        if (getRes.getStatusCode() == 200 && getRes.jsonPath().getList("$").size() >= 2) {
            jobId1 = getRes.jsonPath().getString("[0]._id");
            jobId2 = getRes.jsonPath().getString("[1]._id");
        }

        Allure.addAttachment("Status Code", String.valueOf(getRes.getStatusCode()));
        Allure.addAttachment("Response Body", getRes.prettyPrint());
        AiReporter.addRecord("GET " + GET_JOBS + " → " + getRes.getStatusCode());
    }

    // ---------------- POST ----------------
    @Test(priority = 2, description = "Create new job entry (POST /api/jobs)")
    @Story("Create Operation with AI-generated payload")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateJob() {
        Allure.step("Generating payload dynamically using Gemini AI");
        String payload = AiTestDataGenerator.generatePayload(
                "Generate a JSON payload for a job tracking system with fields: company, role, appliedDate (YYYY-MM-DD), and status set to 'Rejected'."
        );

        Allure.addAttachment("AI Payload", payload);
        Allure.step("Sending POST request to " + POST_JOB);

        Response postRes = ApiReuse.execute(new ApiReuse(POST_JOB, CREATE_METHOD, payload), header);

        Allure.addAttachment("Status Code", String.valueOf(postRes.getStatusCode()));
        Allure.addAttachment("Response Body", postRes.prettyPrint());
        AiReporter.addRecord("POST " + POST_JOB + " → " + postRes.getStatusCode());
    }

    // ---------------- PUT ----------------
    @Test(priority = 3, description = "Update an existing job (PUT /api/jobs/{id})")
    @Story("Update Operation")
    @Severity(SeverityLevel.CRITICAL)
    public void testUpdateJob() {
        Allure.step("Updating status of an existing job to 'Rejected'");
        String payload = "{\"status\": \"Rejected\"}";

        Response putRes = ApiReuse.execute(
                new ApiReuse(PUT_JOB.replace(":id", jobId2), UPDATE_METHOD, payload),
                header
        );

        Allure.addAttachment("Status Code", String.valueOf(putRes.getStatusCode()));
        Allure.addAttachment("Response Body", putRes.prettyPrint());
        AiReporter.addRecord("PUT " + PUT_JOB.replace(":id", jobId2) + " → " + putRes.getStatusCode());
    }

    // ---------------- DELETE ----------------
    @Test(priority = 4, description = "Delete an existing job (DELETE /api/jobs/{id})")
    @Story("Delete Operation")
    @Severity(SeverityLevel.CRITICAL)
    public void testDeleteJob() {
        Allure.step("Deleting a job entry by ID");
        Response delRes = ApiReuse.execute(
                new ApiReuse(DELETE_JOB.replace(":id", jobId1), DELETE_METHOD, null),
                header
        );

        Allure.addAttachment("Status Code", String.valueOf(delRes.getStatusCode()));
        Allure.addAttachment("Response Body", delRes.prettyPrint());
        AiReporter.addRecord("DELETE " + DELETE_JOB.replace(":id", jobId1) + " → " + delRes.getStatusCode());
    }

    // ---------------- AI SUMMARY ----------------
    @Test(priority = 5, description = "Generate AI summary report for all tests")
    @Story("AI Summary + Insights")
    @Severity(SeverityLevel.MINOR)
    public void
    testAiSummaryReport() {
        Allure.step("🧠 Generating AI test summary and saving to file...");
        String aiSummary = AiReporter.generateAndSaveSummary();

        Allure.addAttachment("AI Summary Report", aiSummary);
        System.out.println("\n================ AI SUMMARY REPORT ================\n");
        System.out.println(aiSummary);
        System.out.println("===================================================\n");

        AiReporter.clear();
        Allure.step("🧹 Cleared AI reporter memory for next run.");
    }
}
