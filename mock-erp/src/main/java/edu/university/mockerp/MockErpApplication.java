package edu.university.mockerp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Fictitious personnel, finance and travel ERP (AGENT.md §23). Runs as its own
 * container so that stopping it, or switching a system to "down", is a real
 * outage for Online Personalservices (ADR-007). All data is in memory and fake.
 */
@SpringBootApplication
public class MockErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockErpApplication.class, args);
    }
}
