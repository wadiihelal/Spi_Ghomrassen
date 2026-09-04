package com.promoteur.app.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the French catalogue itself.
 *
 * <p>A doubled apostrophe is how {@code MessageFormat} escapes a quote, and it is stripped only
 * when the message has arguments to substitute. A message with no placeholder is returned
 * verbatim, so a doubled apostrophe there reaches the user as « n''a pas ».</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:spi_ghomrassen_test_messages;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class MessageCatalogTest {

    @Autowired
    private MessageService messageService;

    @Test
    @DisplayName("no message without a placeholder shows a doubled apostrophe to the user")
    void noMessageWithoutAPlaceholderShowsADoubledApostrophe() throws IOException {
        final Properties catalogue = MessageCatalogTest.catalogue();
        final List<String> offenders = new ArrayList<>();

        for (final String key : catalogue.stringPropertyNames()) {
            final String pattern = catalogue.getProperty(key);
            if (!pattern.contains("{") && this.messageService.get(key).contains("''")) {
                offenders.add(key);
            }
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("a message with a placeholder still reads correctly once formatted")
    void aMessageWithAPlaceholderStillReadsCorrectly() {
        assertThat(this.messageService.get("validation.apartment.soldNeedsContract", "A12"))
                .contains("L'appartement A12")
                .doesNotContain("''");
    }

    private static Properties catalogue() throws IOException {
        final Properties properties = new Properties();
        try (InputStream stream = MessageCatalogTest.class.getResourceAsStream("/messages_fr.properties")) {
            properties.load(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
