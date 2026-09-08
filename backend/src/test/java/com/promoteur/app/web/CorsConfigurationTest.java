package com.promoteur.app.web;

import com.promoteur.app.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the only network-level protection the API has (SEC-01, SEC-03).
 *
 * <p>There is no authentication by decision, so what a browser on another origin is allowed to
 * do is decided entirely by {@code CorsConfig} and the {@code app.cors.allowed-origins}
 * property. That configuration also sets {@code allowCredentials(true)}, which makes one
 * mistake unrecoverable: paired with a {@code *} origin it would let any site on the internet
 * issue credentialed requests against the API. Browsers reject that pairing, and so has Spring
 * since 5.3 — but only at runtime, when it is too late to notice.</p>
 *
 * <p>Runs as a full {@code @SpringBootTest} with {@code @AutoConfigureMockMvc} rather than a
 * slice: {@code CorsFilter} is a servlet filter, and a hand-built standalone {@code MockMvc}
 * would never invoke it — leaving the test green for the wrong reason.</p>
 */
@AutoConfigureMockMvc
class CorsConfigurationTest extends AbstractIntegrationTest {

    private static final String CONSOLE_ORIGIN = "http://localhost:4200";
    private static final String FOREIGN_ORIGIN = "https://exemple-malveillant.tn";

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Test
    @DisplayName("the console origin is allowed to preflight a request")
    void theConsoleOriginIsAllowedToPreflight() throws Exception {
        this.mockMvc.perform(options("/api/projects")
                        .header(HttpHeaders.ORIGIN, CONSOLE_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CONSOLE_ORIGIN));
    }

    @Test
    @DisplayName("an origin outside the list is refused")
    void anOriginOutsideTheListIsRefused() throws Exception {
        this.mockMvc.perform(options("/api/projects")
                        .header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("a plain request from a foreign origin gets no permission header back")
    void aPlainRequestFromAForeignOriginGetsNoPermissionHeader() throws Exception {
        // Without Access-Control-Allow-Origin the browser discards the response, whatever the
        // server did with the request. The absence of that header is the protection.
        this.mockMvc.perform(get("/api/projects").header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("credentials are allowed only for a named origin, never for a wildcard")
    void credentialsAreAllowedOnlyForANamedOrigin() throws Exception {
        this.mockMvc.perform(options("/api/projects")
                        .header(HttpHeaders.ORIGIN, CONSOLE_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CONSOLE_ORIGIN));
    }

    @Test
    @DisplayName("the configured origin list never contains a wildcard")
    void theConfiguredOriginListNeverContainsAWildcard() {
        // Guards the property itself, not just the current behaviour: CorsConfig sets
        // allowCredentials(true), so a `*` slipped into any profile's allowed-origins would open
        // the API — which has no authentication — to credentialed requests from anywhere.
        assertThat(this.allowedOrigins)
                .as("app.cors.allowed-origins must name origins, never use a wildcard")
                .isNotBlank()
                .doesNotContain("*");
    }
}
