package org.reso.tests;

import org.junit.jupiter.api.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LookupStringTest {

    @BeforeAll
    void setupServer() throws IOException, InterruptedException {
        System.out.println("Starting RESO Reference Server...");
        ProcessBuilder builder = new ProcessBuilder("docker", "compose", "up", "-d");
        builder.directory(new File(System.getProperty("user.dir")));
        Process process = builder.start();
        process.waitFor();
    }

    private static void setEnv(String key, String value) throws Exception {
        Map<String, String> env = System.getenv();
        Field field = env.getClass().getDeclaredField("m");
        field.setAccessible(true);
        Map<String, String> writableEnv = (Map<String, String>) field.get(env);
        writableEnv.put(key, value);
    }

    @Test
    @Order(1)
    void testLookupStringDD17() throws Exception {
        setEnv("LOOKUP_TYPE", "STRING");
        runDDTest("1.7");
    }

    @Test
    @Order(2)
    void testLookupStringDD20() throws Exception {
        setEnv("LOOKUP_TYPE", "STRING");
        runDDTest("2.0");
    }

    void runDDTest(String version) throws IOException, InterruptedException {
        System.out.println("Starting Enum Data Dictionary Test for DD " + version);

        String lookupType = "STRING";
        ProcessBuilder builder = new ProcessBuilder(
                "cmd.exe", "/c", "reso-certification-utils",
                "runDDTests",
                "-p", "refServLocal.json",
                "-v", version,
                "-l", "10", "-a");
        Map<String, String> env = builder.environment();

        env.put("LOOKUP_TYPE", lookupType);
        builder.directory(new File(System.getProperty("user.dir")));

        System.out.println("Running test with LOOKUP_TYPE = " + System.getenv().get("LOOKUP_TYPE"));
        System.out.println("Executing command: " + String.join(" ", builder.command()));

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

        Assertions.assertEquals(0, exitCode, "DD test command failed!");
    }

    @AfterAll
    void cleanup() throws IOException, InterruptedException {
        System.out.println("Stopping RESO Reference Server...");
        // ProcessBuilder builder = new ProcessBuilder("docker", "compose", "down");
        // builder.directory(new File(System.getProperty("user.dir")));
        // Process process = builder.start();
        // process.waitFor();
    }
}
