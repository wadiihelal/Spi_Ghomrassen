package com.promoteur.app.config;

import com.promoteur.app.dto.SupplierTypeOptionRequest;
import com.promoteur.app.entity.ExpenseCategory;
import com.promoteur.app.entity.VatRateOption;
import com.promoteur.app.repository.ExpenseCategoryRepository;
import com.promoteur.app.repository.SupplierTypeOptionRepository;
import com.promoteur.app.repository.VatRateOptionRepository;
import com.promoteur.app.service.SupplierTypeOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    /**
     * VAT rates levied in Tunisia. Reference data, so the console's dropdown is a query rather
     * than a hard-coded list (CALC-01).
     */
    private static final List<BigDecimal> DEFAULT_VAT_RATES = List.of(
            new BigDecimal("0.0000"),
            new BigDecimal("0.0700"),
            new BigDecimal("0.1300"),
            new BigDecimal("0.1900")
    );

    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final SupplierTypeOptionRepository supplierTypeOptionRepository;
    private final VatRateOptionRepository vatRateOptionRepository;
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
        ensureVatRates();
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

    /** Creates each Tunisian VAT rate that is not already present. */
    private void ensureVatRates() {
        DEFAULT_VAT_RATES.stream()
                .filter(rate -> !vatRateOptionRepository.existsByRate(rate))
                .map(this::buildVatRate)
                .forEach(vatRateOptionRepository::save);
    }

    /** Builds one VAT rate option, labelled as a percentage. */
    private VatRateOption buildVatRate(BigDecimal rate) {
        VatRateOption option = new VatRateOption();
        option.setRate(rate);
        option.setLabel(rate.movePointRight(2).stripTrailingZeros().toPlainString() + " %");
        option.setActive(true);
        return option;
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
