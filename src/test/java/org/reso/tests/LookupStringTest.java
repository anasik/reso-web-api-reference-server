package org.reso.tests;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.junit.jupiter.api.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LookupStringTest {
    private static final Logger LOGGER = Logger.getLogger(LookupStringTest.class.getName());

    @BeforeAll
    void setupServer() throws IOException, InterruptedException {
        LOGGER.info("=========== LookupStringTest Test Suite Starting ===========");
        LOGGER.info("Starting RESO Reference Server for String Lookup Tests...");
        System.out.println("Starting RESO Reference Server for Enum...");

        try {
            ProcessBuilder builder = new ProcessBuilder("docker", "compose", "up", "-d");
            builder.directory(new File(System.getProperty("user.dir")));
            LOGGER.info("Working directory: " + System.getProperty("user.dir"));
            LOGGER.info("Executing command: docker compose up -d");

            Process process = builder.start();
            int exitCode = process.waitFor();
            LOGGER.info("Docker compose process completed with exit code: " + exitCode);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error starting docker containers", e);
            throw e;
        }
    }

    @Test
    @Order(1)
    void testLookupStringDD17() throws Exception {
        LOGGER.info("========== Starting testLookupStringDD17 ==========");
        LOGGER.info("Setting environment variable LOOKUP_TYPE=STRING");
        TestUtils.setEnv("LOOKUP_TYPE", "STRING");

        LOGGER.info("Running DD Test for version 1.7");
        try {
            TestUtils.runDDTest("1.7");
            LOGGER.info("Successfully completed testLookupStringDD17");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in testLookupStringDD17", e);
            throw e;
        }
    }

    @Test
    @Order(2)
    void testLookupStringDD20() throws Exception {
        LOGGER.info("========== Starting testLookupStringDD20 ==========");
        LOGGER.info("Setting environment variable LOOKUP_TYPE=STRING");
        TestUtils.setEnv("LOOKUP_TYPE", "STRING");

        LOGGER.info("Running DD Test for version 2.0");
        try {
            TestUtils.runDDTest("2.0");
            LOGGER.info("Successfully completed testLookupStringDD20");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in testLookupStringDD20", e);
            throw e;
        }
    }

    @AfterAll
    void cleanup() throws IOException, InterruptedException {
        LOGGER.info("========== Test cleanup starting ==========");
        System.out.println("Stopping RESO Reference Server...");

        try {

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error stopping docker containers", e);
            throw e;
        }

        LOGGER.info("=========== LookupStringTest Test Suite Completed ===========");
    }
}
