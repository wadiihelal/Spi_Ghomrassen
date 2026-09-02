package com.promoteur.app.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Declares the {@link MessageSource} explicitly rather than relying on
 * {@code spring.messages.basename}: Spring Boot's auto-configuration only contributes a bean
 * when {@code messages.properties} itself exists, and this application ships a single
 * locale-suffixed bundle, {@code messages_fr.properties}.
 */
@Configuration
public class MessageSourceConfig {

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }
}
