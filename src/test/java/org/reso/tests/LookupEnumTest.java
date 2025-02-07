package org.reso.tests;

import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;

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
    void testLookupEnumDD17() throws Exception {
        TestUtils.setEnv("LOOKUP_TYPE", "ENUM_COLLECTION");
        TestUtils.runDDTest("1.7");
    }

    @Test
    @Order(2)
    void testLookupEnumDD20() throws Exception {
        TestUtils.setEnv("LOOKUP_TYPE", "ENUM_COLLECTION");
        TestUtils.runDDTest("2.0");
    }

    @AfterAll
    void cleanup() throws IOException, InterruptedException {
        System.out.println("Stopping RESO Reference Server...");
    }
}
