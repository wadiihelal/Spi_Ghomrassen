package com.promoteur.app.service.impl;

import com.promoteur.app.repository.ClientAdvanceRepository;
import com.promoteur.app.repository.ClientPurchaseRepository;
import com.promoteur.app.repository.ExpenseRepository;
import com.promoteur.app.service.ReferenceGeneratorService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class ReferenceGeneratorServiceImpl implements ReferenceGeneratorService {

    private static final String EXPENSE_SEQUENCE = "expense_ref_seq";
    private static final String ADVANCE_SEQUENCE = "advance_ref_seq";
    private static final String PURCHASE_SEQUENCE = "purchase_ref_seq";

    /**
     * Guards against an endless loop if every candidate were somehow taken. A reference is
     * skipped only when it collides with a hand-entered or repaired one, so a handful of
     * attempts is already generous.
     */
    private static final int MAX_ATTEMPTS = 100;

    @PersistenceContext
    private EntityManager entityManager;

    private final ExpenseRepository expenseRepository;
    private final ClientAdvanceRepository clientAdvanceRepository;
    private final ClientPurchaseRepository clientPurchaseRepository;

    @Override
    public String nextExpenseReference(final LocalDate date) {
        return this.nextReference("DEP", EXPENSE_SEQUENCE, date,
                reference -> this.expenseRepository.findByReference(reference).isPresent());
    }

    @Override
    public String nextAdvanceReference(final LocalDate date) {
        return this.nextReference("ACC", ADVANCE_SEQUENCE, date,
                reference -> this.clientAdvanceRepository.findByReference(reference).isPresent());
    }

    @Override
    public String nextPurchaseReference(final LocalDate date) {
        return this.nextReference("ACH", PURCHASE_SEQUENCE, date,
                reference -> this.clientPurchaseRepository.findByReference(reference).isPresent());
    }

    /**
     * Draws sequence values until one yields an unused reference. References entered by hand,
     * and those repaired by migration V7, are outside the sequence's control, so a candidate is
     * checked before being handed out.
     */
    private String nextReference(final String prefix, final String sequenceName, final LocalDate date,
                                final Predicate<String> alreadyTaken) {
        final int year = (date == null ? LocalDate.now() : date).getYear();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            final String candidate = String.format("%s-%d-%05d", prefix, year, this.nextSequenceValue(sequenceName));
            if (!alreadyTaken.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Impossible d'attribuer une référence libre sur la séquence " + sequenceName);
    }

    /**
     * Reads the next value of a database sequence. The statement is produced by the configured
     * Hibernate dialect, so the same code serves PostgreSQL and the H2 test database.
     */
    private long nextSequenceValue(final String sequenceName) {
        final SessionFactoryImplementor sessionFactory = this.entityManager.getEntityManagerFactory()
                .unwrap(SessionFactoryImplementor.class);
        final String sql = sessionFactory.getJdbcServices().getDialect()
                .getSequenceSupport().getSequenceNextValString(sequenceName);
        return ((Number) this.entityManager.createNativeQuery(sql).getSingleResult()).longValue();
    }
}
