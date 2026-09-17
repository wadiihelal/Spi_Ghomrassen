package com.promoteur.app.web;

import com.promoteur.app.config.MessageSourceConfig;
import com.promoteur.app.exception.GlobalExceptionHandler;
import com.promoteur.app.shared.MessageServiceImpl;
import com.promoteur.app.shared.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers API-02 at the only place where an exception becomes an HTTP response.
 *
 * <p>{@link GlobalExceptionHandler} decides what the Angular console sees when something goes
 * wrong, and nothing exercised it. The console reads {@code error} and {@code details} out of
 * the body and shows them in a toast, so both the status and the shape of that body are a
 * contract — one that was never verified.</p>
 *
 * <p>A dedicated probe controller raises each exception on demand: driving a real controller
 * would mean setting up a service just to make it fail, and would say less about which branch
 * of the handler is under test.</p>
 */
// `controllers` keeps the slice from scanning the nineteen real controllers and demanding a bean
// for every service behind them; the probe still has to be imported, because a class nested in a
// test is not a component-scan candidate — without that, requests fall through to the static
// resource handler and every assertion sees the 500 raised by a NoResourceFoundException.
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@ActiveProfiles("test")
@Import({GlobalExceptionHandlerTest.ProbeController.class, GlobalExceptionHandler.class,
        MessageServiceImpl.class, MessageSourceConfig.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("an unknown route answers 404 rather than 500")
    void anUnknownRouteAnswers404RatherThan500() throws Exception {
        // A URL matching no controller falls through to the static-resource handler. Without an
        // explicit branch it reached the catch-all and every typo answered « Unexpected server
        // error » — indistinguishable from a genuinely broken endpoint (17/09/2026).
        this.mockMvc.perform(get("/api/route-qui-nexiste-pas"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Cette adresse n'existe pas sur le serveur."));
    }

    @Test
    @DisplayName("a missing resource answers 404 with the message the service raised")
    void aMissingResourceAnswers404() throws Exception {
        this.mockMvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Acompte introuvable (identifiant 42)."));
    }

    @Test
    @DisplayName("an invalid payload answers 400 with one detail per rejected field")
    void anInvalidPayloadAnswers400WithOneDetailPerField() throws Exception {
        this.mockMvc.perform(post("/probe/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": -5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details.label").exists())
                .andExpect(jsonPath("$.details.amount").exists());
    }

    @Test
    @DisplayName("a refused business rule answers 400 with its French message")
    void aRefusedBusinessRuleAnswers400() throws Exception {
        this.mockMvc.perform(get("/probe/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Le taux de TVA -0.5 ne peut pas être négatif."));
    }

    @Test
    @DisplayName("a concurrent modification answers 409 in accented French")
    void aConcurrentModificationAnswers409() throws Exception {
        this.mockMvc.perform(get("/probe/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value(
                        "L'enregistrement vient d'être modifié par une autre opération. "
                                + "Rechargez la page puis recommencez."));
    }

    @Test
    @DisplayName("an oversized upload answers 413 in accented French")
    void anOversizedUploadAnswers413() throws Exception {
        this.mockMvc.perform(get("/probe/upload-too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value(
                        "Le fichier dépasse la taille maximale autorisée (10 Mo)."));
    }

    @Test
    @DisplayName("a duplicate reference answers 409 rather than 500")
    void aDuplicateReferenceAnswers409RatherThan500() throws Exception {
        this.mockMvc.perform(get("/probe/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("an unknown enum value answers 400 rather than 500")
    void anUnknownEnumValueAnswers400RatherThan500() throws Exception {
        this.mockMvc.perform(get("/probe/enum").param("ownerType", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("a non-numeric identifier answers 400 rather than 500")
    void aNonNumericIdentifierAnswers400RatherThan500() throws Exception {
        this.mockMvc.perform(get("/probe/by-id/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("malformed json answers 400 rather than 500")
    void malformedJsonAnswers400RatherThan500() throws Exception {
        this.mockMvc.perform(post("/probe/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("a missing required parameter answers 400 naming the parameter")
    void aMissingRequiredParameterAnswers400NamingTheParameter() throws Exception {
        this.mockMvc.perform(get("/probe/required-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("year")));
    }

    @Test
    @DisplayName("an unexpected failure answers 500 without saying what broke")
    void anUnexpectedFailureAnswers500() throws Exception {
        this.mockMvc.perform(get("/probe/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Unexpected server error"));
    }

    @Test
    @DisplayName("validation messages reach the console in French, whatever the server locale")
    void validationMessagesReachTheConsoleInFrench() throws Exception {
        final String body = this.mockMvc.perform(post("/probe/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": -5}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // getContentAsString() without a charset falls back to ISO-8859-1, unlike the jsonPath
        // matchers which decode UTF-8 — reading the body by hand needs the charset spelled out,
        // or a correct accented message looks like mojibake to the test alone.
        // The whole console is French; a field error that arrives as "must not be blank"
        // because the server happens to run under en_TN is a leak of the runtime environment
        // into the user interface. Asserting the French text as well as the absence of the
        // English one also proves the bundle is decoded as UTF-8 — a mojibake accent would
        // fail here rather than reach a user's screen.
        assertThat(body)
                .doesNotContain("must not be blank")
                .doesNotContain("must be greater than 0")
                .contains("Ce champ ne peut pas être vide.")
                .contains("La valeur doit être strictement supérieure à zéro.");
    }

    @Test
    @DisplayName("no error response ever leaks a stack trace or a SQL statement")
    void noErrorResponseEverLeaksAStackTraceOrSql() throws Exception {
        for (final String path : new String[]{"/probe/unexpected", "/probe/data-integrity"}) {
            final String body = this.mockMvc.perform(get(path))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

            assertThat(body).as("body of %s", path)
                    .doesNotContain("at com.promoteur")
                    .doesNotContain("insert into")
                    .doesNotContain("constraint")
                    .doesNotContain("ConstraintViolationException");
        }
    }

    @Test
    @DisplayName("every error response carries timestamp, status and error")
    void everyErrorResponseCarriesTimestampStatusAndError() throws Exception {
        for (final String path : new String[]{"/probe/not-found", "/probe/illegal-argument",
                "/probe/optimistic-lock", "/probe/upload-too-large", "/probe/data-integrity",
                "/probe/required-param", "/probe/unexpected"}) {
            this.mockMvc.perform(get(path))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    /**
     * Stands in for {@code AttachmentOwnerType}, which the upload endpoint takes as a param.
     */
    enum ProbeOwnerType {
        EXPENSE, CLIENT_ADVANCE
    }

    /**
     * Raises one exception per route, so each branch of the handler is reachable on its own.
     */
    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Acompte introuvable (identifiant 42).");
        }

        @GetMapping("/illegal-argument")
        void illegalArgument() {
            throw new IllegalArgumentException("Le taux de TVA -0.5 ne peut pas être négatif.");
        }

        @GetMapping("/optimistic-lock")
        void optimisticLock() {
            throw new OptimisticLockingFailureException("row was updated");
        }

        @GetMapping("/upload-too-large")
        void uploadTooLarge() {
            throw new MaxUploadSizeExceededException(10_485_760L);
        }

        /**
         * What a duplicate reference or a broken foreign key really raises.
         */
        @GetMapping("/data-integrity")
        void dataIntegrity() {
            throw new DataIntegrityViolationException(
                    "could not execute statement [ERROR: duplicate key value violates unique "
                            + "constraint \"uk_expenses_reference\"] [insert into expenses ...]");
        }

        @GetMapping("/enum")
        void unknownEnum(@RequestParam final ProbeOwnerType ownerType) {
            throw new IllegalStateException("never reached: the conversion fails first");
        }

        @GetMapping("/by-id/{id}")
        void byId(@PathVariable final Long id) {
            throw new IllegalStateException("never reached: the conversion fails first");
        }

        @GetMapping("/required-param")
        void requiredParam(@RequestParam final Integer year) {
            throw new IllegalStateException("never reached: the missing parameter fails first");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("a NullPointerException deep in a mapper, say");
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody final ProbePayload payload) {
            // Reached only when the payload is valid; these tests always send an invalid one.
        }
    }

    /**
     * Two constraints, so a single request produces two field errors.
     */
    static class ProbePayload {

        @NotBlank
        private String label;

        @NotNull
        @Positive
        private BigDecimal amount;

        public String getLabel() {
            return this.label;
        }

        public void setLabel(final String label) {
            this.label = label;
        }

        public BigDecimal getAmount() {
            return this.amount;
        }

        public void setAmount(final BigDecimal amount) {
            this.amount = amount;
        }
    }
}
