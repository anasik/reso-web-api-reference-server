package org.reso.tests;

import org.junit.jupiter.api.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LookupEnumTest {

    @BeforeAll
    void setupServer() throws IOException, InterruptedException {
        System.out.println("Starting RESO Reference Server for Enum...");
        ProcessBuilder builder = new ProcessBuilder("docker", "compose", "up", "-d");
        builder.directory(new File(System.getProperty("user.dir")));
        Process process = builder.start();
        process.waitFor();
    }

    @Test
    @Order(1)
    void testLookupEnumDD17() throws IOException, InterruptedException {
        runDDTest("1.7");
    }

    // @Test
    // @Order(2)
    // void testLookupEnumDD20() throws IOException, InterruptedException {
    // runDDTest("2.0");
    // }

    void runDDTest(String version) throws IOException, InterruptedException {
        System.out.println("Starting Enum Data Dictionary Test for DD " + version);

        ProcessBuilder builder = new ProcessBuilder(
                "cmd.exe", "/c", "reso-certification-utils",
                "runDDTests",
                "-p", "refServLocal.json",
                "-v", version,
                "-l", "10"
        // ,"-a"
        );

        builder.directory(new File(System.getProperty("user.dir")));

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
    void cleanup() {
        System.out.println("Skipping Docker shutdown after tests.");
        // ProcessBuilder builder = new ProcessBuilder("docker", "compose", "down");
        // builder.directory(new File(System.getProperty("user.dir")));
        // Process process = builder.start();
        // process.waitFor();
    }

}
