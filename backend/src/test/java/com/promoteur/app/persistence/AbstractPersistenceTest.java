package com.promoteur.app.persistence;

import com.promoteur.app.apartment.Apartment;
import com.promoteur.app.client.Client;
import com.promoteur.app.project.Project;
import com.promoteur.app.project.ProjectStatus;
import com.promoteur.app.supplier.Supplier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

/**
 * Base class for the tests that exercise a {@code repository/specification} directly (PERF-02).
 *
 * <p>{@code @DataJpaTest} loads the persistence layer alone and wraps each test in a transaction
 * that rolls back, so these classes need no {@link com.promoteur.app.DatabaseCleaner}.
 * {@code @AutoConfigureTestDatabase(NONE)} keeps the configured datasource instead of swapping in
 * an empty one, so Flyway builds the real schema — the point being to test against it.</p>
 *
 * <p>Note that {@code ReferenceDataInitializer} does <b>not</b> run here: a
 * {@code CommandLineRunner} needs a started application. Every fixture is therefore built by the
 * test itself, which is what these tests want anyway.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
abstract class AbstractPersistenceTest {

    @PersistenceContext
    protected EntityManager entityManager;

    /**
     * Applies a specification to a bare criteria query and reports the associations it joined,
     * one entry per join.
     *
     * <p>Measured on the criteria tree rather than on the emitted SQL: a surplus join changes no
     * row, so no assertion on the result can see it, and Hibernate may prune a join it does not
     * end up using — which would hide a defect that is nonetheless in the query the code
     * builds.</p>
     */
    protected <T> List<String> joinsOf(final Class<T> entity, final Specification<T> specification) {
        final CriteriaBuilder builder = this.entityManager.getCriteriaBuilder();
        final CriteriaQuery<T> query = builder.createQuery(entity);
        final Root<T> root = query.from(entity);

        specification.toPredicate(root, query, builder);

        return root.getJoins().stream()
                .map(join -> join.getAttribute().getName())
                .toList();
    }

    protected Project persistProject(final String code, final String name) {
        final Project project = new Project();
        project.setCode(code);
        project.setName(name);
        project.setStatus(ProjectStatus.IN_PROGRESS);
        this.entityManager.persist(project);
        return project;
    }

    protected Client persistClient(final Project project, final String fullName) {
        final Client client = new Client();
        client.setFullName(fullName);
        client.setProject(project);
        this.entityManager.persist(client);
        return client;
    }

    protected Supplier persistSupplier(final String name) {
        final Supplier supplier = new Supplier();
        supplier.setName(name);
        this.entityManager.persist(supplier);
        return supplier;
    }

    protected Apartment persistApartment(final Project project, final String number,
                                         final BigDecimal salePrice) {
        final Apartment apartment = new Apartment();
        apartment.setApartmentNumber(number);
        apartment.setApartmentType("S+2");
        apartment.setTotalSurface(new BigDecimal("100.000"));
        apartment.setTotalSalePrice(salePrice);
        apartment.setProject(project);
        this.entityManager.persist(apartment);
        return apartment;
    }

    /**
     * Flushes the fixture and clears the session, so finders read from the database.
     */
    protected void settleFixture() {
        this.entityManager.flush();
        this.entityManager.clear();
    }
}
