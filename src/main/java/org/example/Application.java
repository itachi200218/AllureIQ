package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> webServerFactoryCustomizer() {
        return factory -> {
            // Render provides a dynamic port using the PORT environment variable.
            String port = System.getenv("PORT");

            if (port != null) {
                // When deployed on Render
                factory.setPort(Integer.parseInt(port));
            } else {
                // When running locally
                factory.setPort(8081);
            }
        };
    }
}
