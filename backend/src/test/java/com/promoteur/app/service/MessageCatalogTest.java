package com.promoteur.app.service;

import com.promoteur.app.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the French catalogue itself.
 *
 * <p>A doubled apostrophe is how {@code MessageFormat} escapes a quote, and it is stripped only
 * when the message has arguments to substitute. A message with no placeholder is returned
 * verbatim, so a doubled apostrophe there reaches the user as « n''a pas ».</p>
 */
class MessageCatalogTest extends AbstractIntegrationTest {

    /** An apostrophe that is neither preceded nor followed by another one. */
    private static final Pattern SINGLE_APOSTROPHE = Pattern.compile("(?<!')'(?!')");

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

    @Test
    @DisplayName("no message with a placeholder loses an apostrophe to MessageFormat")
    void noMessageWithAPlaceholderLosesAnApostrophe() throws IOException {
        final Properties catalogue = MessageCatalogTest.catalogue();
        final List<String> offenders = new ArrayList<>();

        // The mirror of the test above, and the case it missed. In a message that has a
        // placeholder, MessageFormat reads a lone apostrophe as a quoting character and drops
        // it: « n'est » reaches the user as « nest ». It has to be doubled — and a doubled one
        // is only correct here, which is why the two tests must both exist.
        for (final String key : catalogue.stringPropertyNames()) {
            final String pattern = catalogue.getProperty(key);
            if (pattern.matches("(?s).*\\{\\d.*") && SINGLE_APOSTROPHE.matcher(pattern).find()) {
                offenders.add(key + " → " + pattern);
            }
        }

        assertThat(offenders).isEmpty();
    }

    private static Properties catalogue() throws IOException {
        final Properties properties = new Properties();
        try (InputStream stream = MessageCatalogTest.class.getResourceAsStream("/messages_fr.properties")) {
            properties.load(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
