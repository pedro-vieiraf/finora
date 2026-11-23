package com.pedro.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Finora application.
 */
@SpringBootApplication
@EnableScheduling
public class FinoraApplication {

  /**
   * Starts the Finora application.
   *
   * @param args application command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(FinoraApplication.class, args);
  }
}