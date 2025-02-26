package org.reso.tests;

import org.junit.jupiter.api.Assertions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.Map;
import java.nio.file.Files;

public class TestUtils {
    private static final String CONFIG_FILE = "refServLocal.json";
    private static final String LIMIT = "-l 10";

    /**
     * Sets an environment variable dynamically within the JVM.
     *
     * @param key   The environment variable name
     * @param value The value to assign
     */
    @SuppressWarnings("unchecked")
    public static void setEnv(String key, String value) throws Exception {
        Map<String, String> env = System.getenv();
        Field field = env.getClass().getDeclaredField("m");
        field.setAccessible(true);
        Map<String, String> writableEnv = (Map<String, String>) field.get(env);
        writableEnv.put(key, value);

        System.out.println("Set environment variable: " + key + " = " + value);
    }

    /**
     * Runs the Data Dictionary test using the reso-certification-utils CLI.
     *
     * @param version The Data Dictionary version (e.g., "1.7" or "2.0")
     */
    public static void runDDTest(String version) throws IOException, InterruptedException {
        String lookupType = System.getenv("LOOKUP_TYPE");
        System.out.println("Running test with LOOKUP_TYPE = " + lookupType);
        ProcessBuilder builder;

        if (isWindows()) {
            System.out.println("isWindows is true");
            builder = new ProcessBuilder("cmd.exe", "/c",
                    "reso-certification-utils",
                    "runDDTests",
                    "-p", CONFIG_FILE,
                    "-v", version,
                    LIMIT, "-a");
        } else {
            System.out.println("isWindows is false");
            builder = new ProcessBuilder(
                    "reso-certification-utils",
                    "runDDTests",
                    "-p", CONFIG_FILE,
                    "-v", version,
                    LIMIT, "-a");
        }

        builder.directory(new File(System.getProperty("user.dir")));
        Process process = builder.start();

        // Read process output (stdout)
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("[CMD] " + line); // Print live output
        }

        // Read process error (stderr)
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        while ((line = errorReader.readLine()) != null) {
            System.err.println("[ERROR] " + line); // Print error output
        }

        int exitCode = process.waitFor();
        System.out.println("Process finished with exit code: " + exitCode);

        // Get the dynamic folder path from refServLocal.json
        String dynamicFolder = getDynamicFolder();
        System.out.println("Dynamic folder path: " + dynamicFolder);

        String responsePathFull = System.getProperty("user.dir")
                + "/results/data-dictionary-"
                + version + "/" + dynamicFolder + "/current/data-dictionary-" + version + ".json";
        File responseFullFile = new File(responsePathFull);

        Assertions.assertTrue(responseFullFile.exists(), "Response JSON file not found!");

        System.out.println("Test completed for DD version " + version);
    }

    /**
     * Checks if the operating system is Windows.
     *
     * @return True if running on Windows, false otherwise.
     */
    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Reads `refServLocal.json` and extracts providerUoi, providerUsi, and
     * recipientUoi to build the dynamic folder path.
     *
     * @return The constructed folder path in the format
     *         "providerUoi-providerUsi/recipientUoi"
     */
    private static String getDynamicFolder() throws IOException {
        String jsonFilePath = System.getProperty("user.dir") + "/" + CONFIG_FILE;
        String jsonContent = new String(Files.readAllBytes(Paths.get(jsonFilePath)));

        JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
        String providerUoi = jsonObject.get("providerUoi").getAsString();
        JsonObject config = jsonObject.getAsJsonArray("configs").get(0).getAsJsonObject();
        String providerUsi = config.get("providerUsi").getAsString();
        String recipientUoi = config.get("recipientUoi").getAsString();

        return providerUoi + "-" + providerUsi + "/" + recipientUoi;
    }

    // example: $ java -jar build/libs/web-api-commander.jar --saveGetRequest --uri
    // 'https://api.server.com/OData/Property?$filter=ListPrice gt 100000&$top=100'
    // --bearerToken abc123 --outputFile response.
    // ./gradlew testWebApiCore -D${ARG_VERSION}=${DEFAULT_WEB_API_VERSION}
    // -D${ARG_RESOSCRIPT_PATH}=/path/to/web-api-core.resoscript
    // -D${ARG_SHOW_RESPONSES}=true" +
    // java -jar build/libs/web-api-commander.jar --runRESOScript --inputFile
    // webapiserver.resoscript --generateQueries
    public static int runWebApiCoreTest() throws IOException, InterruptedException {
        String lookupType = System.getenv("LOOKUP_TYPE");
        System.out.println("Running Web API Core test with LOOKUP_TYPE = " + lookupType);

        // Determine the correct command
        ProcessBuilder builder;
        if (isWindows()) {
            builder = new ProcessBuilder("cmd.exe", "/c",
                    "", "testWebApiCore ",
                    "-d", "2.1.0",
                    "-u", "http://localhost:8080");
        } else {
            builder = new ProcessBuilder(
                    "", "testWebApiCore",
                    "-d", "2.1.0",
                    "-u", "http://localhost:8080");
        }

        builder.directory(new File(System.getProperty("user.dir")));
        Process process = builder.start();

        // Capture output for debugging
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        reader.lines().forEach(System.out::println);

        // Capture errors
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        errorReader.lines().forEach(System.err::println);

        // Wait for process to finish
        int exitCode = process.waitFor();
        System.out.println("Web API Core Test exited with code: " + exitCode);

        return exitCode;
    }

}
