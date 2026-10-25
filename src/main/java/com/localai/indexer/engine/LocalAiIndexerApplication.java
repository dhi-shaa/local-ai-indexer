package com.localai.indexer.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main Spring Boot Context Entry Point.
 * Optimized for headless background execution alongside a local desktop JavaFX UI.
 * Exposes both direct Java API access and REST HTTP endpoints for decoupled polling.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.localai.indexer")
public class LocalAiIndexerApplication {

    private static final Logger log = LoggerFactory.getLogger(LocalAiIndexerApplication.class);
    private static ConfigurableApplicationContext context;

    public static void main(String[] args) {
        // Enforce headless operation for desktop embedding
        System.setProperty("java.awt.headless", "true");
        log.info("Starting Local AI Privacy Chat & Document Indexer Backend...");
        context = SpringApplication.run(LocalAiIndexerApplication.class, args);
        log.info("Backend Engine initialized successfully. Ready for JavaFX pairing.");
    }

    /**
     * Programmatic starter for JavaFX lifecycle binding.
     */
    public static synchronized ConfigurableApplicationContext startHeadless(String... args) {
        if (context == null || !context.isActive()) {
            System.setProperty("java.awt.headless", "true");
            SpringApplication app = new SpringApplication(LocalAiIndexerApplication.class);
            app.setHeadless(true);
            context = app.run(args);
        }
        return context;
    }

    /**
     * Programmatic shutdown for clean application teardown when desktop window closes.
     */
    public static synchronized void stopHeadless() {
        if (context != null && context.isActive()) {
            context.close();
            context = null;
        }
    }

    /**
     * Direct method reference access for JavaFX frontend in shared JVM mode.
     */
    public static <T> T getBean(Class<T> beanClass) {
        if (context == null) {
            throw new IllegalStateException("Spring Boot context has not been initialized. Call startHeadless() first.");
        }
        return context.getBean(beanClass);
    }

    public static ConfigurableApplicationContext getContext() {
        return context;
    }
}
