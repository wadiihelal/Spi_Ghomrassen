package com.promoteur.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Titre et description du document OpenAPI servi sous /v3/api-docs et /swagger-ui.html dans
 * les profils dev et test (API-02). Absent en production.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI spiGhomrassenOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SPI Ghomrassen — API")
                .version("v1")
                .description("Gestion interne d'un promoteur immobilier : projets, appartements, clients, "
                        + "contrats de vente, acomptes, factures fournisseurs, dépenses et rapports. "
                        + "Montants en dinars tunisiens, 3 décimales (millimes). "
                        + "Aucune authentification : l'API doit rester inaccessible depuis l'extérieur."));
    }
}
