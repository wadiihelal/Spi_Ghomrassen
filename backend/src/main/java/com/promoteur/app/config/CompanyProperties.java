package com.promoteur.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The promoter's own identity, printed on every document it issues (UX-06).
 *
 * <p>Configured rather than compiled in: a tax identifier or an address changes without a
 * release, and the same build serves a second company.</p>
 */
@ConfigurationProperties(prefix = "app.company")
public record CompanyProperties(
        String name,
        String legalForm,
        String address,
        String taxId,
        String phone,
        String email
) {

    /** Blank fields are simply left off the letterhead rather than printed empty. */
    public boolean has(final String value) {
        return value != null && !value.isBlank();
    }
}
