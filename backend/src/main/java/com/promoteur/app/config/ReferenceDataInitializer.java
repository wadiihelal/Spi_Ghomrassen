package com.promoteur.app.config;

import com.promoteur.app.dto.SupplierTypeOptionRequest;
import com.promoteur.app.entity.ExpenseCategory;
import com.promoteur.app.repository.ExpenseCategoryRepository;
import com.promoteur.app.repository.SupplierTypeOptionRepository;
import com.promoteur.app.service.SupplierTypeOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application start-up seed for reference data only: expense categories and supplier type
 * options. Runs in every profile and creates exclusively what is missing, so an operator who
 * deleted one label gets it back on the next start without duplicating the others.
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class ReferenceDataInitializer implements CommandLineRunner {

    /** Default expense categories offered by the console. */
    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "Frais Baladiya",
            "Frais Ingénieurs",
            "Frais Fournisseurs",
            "Frais Administratifs",
            "Frais Notaire",
            "Autres"
    );

    /** Default supplier type options offered by the console. */
    private static final List<String> DEFAULT_SUPPLIER_TYPES = List.of(
            "Fournisseur",
            "Ingénieur",
            "Architecte",
            "Entrepreneur",
            "Autre"
    );

    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final SupplierTypeOptionRepository supplierTypeOptionRepository;
    private final SupplierTypeOptionService supplierTypeOptionService;

    /**
     * Runs after the application context is loaded, before {@link DemoDataInitializer}.
     *
     * @param args standard {@link CommandLineRunner} arguments (unused)
     */
    @Override
    @Transactional
    public void run(String... args) {
        ensureCategories();
        ensureSupplierTypes();
    }

    /** Creates each default expense category that is not already present. */
    private void ensureCategories() {
        DEFAULT_CATEGORIES.stream()
                .filter(name -> !expenseCategoryRepository.existsByName(name))
                .map(this::buildCategory)
                .forEach(expenseCategoryRepository::save);
    }

    /** Creates each default supplier type option that is not already present. */
    private void ensureSupplierTypes() {
        DEFAULT_SUPPLIER_TYPES.stream()
                .filter(label -> !supplierTypeOptionRepository.existsByLabel(label))
                .forEach(this::saveSupplierType);
    }

    /** Persists one supplier type option with the given label. */
    private void saveSupplierType(String label) {
        SupplierTypeOptionRequest request = new SupplierTypeOptionRequest();
        request.setLabel(label);
        request.setActive(true);
        supplierTypeOptionService.create(request);
    }

    /** Builds a bare {@link ExpenseCategory} for {@link #ensureCategories()} inserts. */
    private ExpenseCategory buildCategory(String name) {
        ExpenseCategory category = new ExpenseCategory();
        category.setName(name);
        return category;
    }
}
