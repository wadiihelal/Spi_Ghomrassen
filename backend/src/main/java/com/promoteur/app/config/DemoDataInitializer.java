package com.promoteur.app.config;

import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.dto.ClientAdvanceRequest;
import com.promoteur.app.dto.ClientPurchaseRequest;
import com.promoteur.app.dto.ExpenseRequest;
import com.promoteur.app.entity.Apartment;
import com.promoteur.app.entity.Client;
import com.promoteur.app.entity.ExpenseCategory;
import com.promoteur.app.entity.Project;
import com.promoteur.app.entity.Supplier;
import com.promoteur.app.entity.SupplierTypeOption;
import com.promoteur.app.enums.PaymentMethod;
import com.promoteur.app.enums.ProjectStatus;
import com.promoteur.app.repository.ApartmentRepository;
import com.promoteur.app.repository.ClientAdvanceRepository;
import com.promoteur.app.repository.ClientPurchaseRepository;
import com.promoteur.app.repository.ClientRepository;
import com.promoteur.app.repository.ExpenseCategoryRepository;
import com.promoteur.app.repository.ProjectRepository;
import com.promoteur.app.repository.SupplierRepository;
import com.promoteur.app.repository.SupplierTypeOptionRepository;
import com.promoteur.app.service.ApartmentService;
import com.promoteur.app.service.ClientAdvanceService;
import com.promoteur.app.service.ClientPurchaseService;
import com.promoteur.app.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fictitious business data for demonstrations: projects, suppliers, clients, apartments,
 * purchases, advances and expenses, plus the five SPI demo residences.
 *
 * <p>Active only under the {@code demo} profile. Never enable it against a production
 * database: it injects synthetic buyers into live accounting.</p>
 */
@Component
@Profile("demo")
@Order(2)
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoDataInitializer.class);

    /** Number of floors generated for each SPI demo residence. */
    private static final int DEMO_RESIDENCE_FLOORS = 6;
    /** Apartments created on each floor of a demo residence. */
    private static final int DEMO_APARTMENTS_PER_FLOOR = 4;
    /** Maximum demo clients assigned as acquirers per residence. */
    private static final int DEMO_CLIENTS_PER_RESIDENCE = 24;
    /** Rotating given names for synthetic demo clients. */
    private static final String[] DEMO_FIRST_NAMES = {
            "Ahmed", "Amine", "Sami", "Youssef", "Mohamed", "Karim", "Walid", "Hatem",
            "Imen", "Sarra", "Nour", "Rim", "Nadia", "Meriem", "Ines", "Amani"
    };
    /** Rotating family names paired with {@link #DEMO_FIRST_NAMES}. */
    private static final String[] DEMO_LAST_NAMES = {
            "Ben Salem", "Trabelsi", "Gharbi", "Kchaou", "Bouzid", "Dammak", "Mansouri", "Jemai",
            "Ayari", "Mejri", "Abidi", "Haddad", "Khemiri", "Sassi", "Ltaief", "Mrad"
    };
    /** Payment methods cycled when generating demo client advances. */
    private static final PaymentMethod[] DEMO_PAYMENT_METHODS = {
            PaymentMethod.BANK_TRANSFER, PaymentMethod.CHECK, PaymentMethod.CASH, PaymentMethod.CARD
    };

    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;
    private final ProjectRepository projectRepository;
    private final ApartmentRepository apartmentRepository;
    private final ClientPurchaseRepository clientPurchaseRepository;
    private final ClientAdvanceRepository clientAdvanceRepository;
    private final ExpenseService expenseService;
    private final ApartmentService apartmentService;
    private final ClientAdvanceService clientAdvanceService;
    private final ClientPurchaseService clientPurchaseService;
    private final SupplierTypeOptionRepository supplierTypeOptionRepository;

    /**
     * Runs after the application context is loaded. Idempotent: skips heavy seeding when core
     * entities already exist; demo residences are reconciled every time.
     *
     * @param args standard {@link CommandLineRunner} arguments (unused)
     */
    @Override
    @Transactional
    public void run(String... args) {
        LOGGER.warn("Profil demo actif — chargement de données fictives. Ne jamais activer en production.");

        if (shouldSkipBusinessSeed()) {
            ensureDemoResidenceData();
            return;
        }

        Map<String, ExpenseCategory> categories = expenseCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(ExpenseCategory::getName, value -> value, (a, b) -> a, HashMap::new));

        Map<String, SupplierTypeOption> supplierTypes = supplierTypeOptionRepository.findAll().stream()
                .collect(Collectors.toMap(SupplierTypeOption::getLabel, value -> value, (a, b) -> a, HashMap::new));

        Map<String, Project> projects = seedProjects();
        Map<String, Supplier> suppliers = seedSuppliers(supplierTypes);
        Map<String, Client> clients = seedClients(projects);

        seedExpenses(categories, suppliers, projects);
        seedApartments(clients, projects);
        seedClientAdvances(clients, projects);
        seedClientPurchases(clients, projects);
        ensureDemoResidenceData();
    }

    /**
     * @return {@code true} when at least one client, supplier, or project already exists so the
     *         full business seed (projects, suppliers, sample expenses, etc.) is skipped.
     */
    private boolean shouldSkipBusinessSeed() {
        return clientRepository.count() > 0 || supplierRepository.count() > 0 || projectRepository.count() > 0;
    }

    /**
     * Creates or updates five demo SPI residences with apartments, optional acquirers, purchases,
     * and advances for UI demos.
     */
    private void ensureDemoResidenceData() {
        List<DemoResidenceSpec> residences = List.of(
                new DemoResidenceSpec("SPI-DEMO-RES-A", "Résidence Démo El Hana", "A", "Ghomrassen centre", ProjectStatus.IN_PROGRESS, new BigDecimal("1850000.000"), LocalDate.of(2025, 9, 1), LocalDate.of(2027, 2, 28)),
                new DemoResidenceSpec("SPI-DEMO-RES-B", "Résidence Démo Les Oliviers", "B", "Route de Tataouine, Ghomrassen", ProjectStatus.IN_PROGRESS, new BigDecimal("2100000.000"), LocalDate.of(2025, 11, 15), LocalDate.of(2027, 6, 30)),
                new DemoResidenceSpec("SPI-DEMO-RES-C", "Résidence Démo Ghomrassen Nord", "C", "Ghomrassen nord", ProjectStatus.PLANNED, new BigDecimal("1650000.000"), LocalDate.of(2026, 6, 1), LocalDate.of(2028, 1, 31)),
                new DemoResidenceSpec("SPI-DEMO-RES-D", "Résidence Démo Jasmin Livrée", "D", "Centre-ville Ghomrassen", ProjectStatus.COMPLETED, new BigDecimal("1760000.000"), LocalDate.of(2023, 5, 10), LocalDate.of(2025, 12, 20)),
                new DemoResidenceSpec("SPI-DEMO-RES-E", "Résidence Démo El Waha Livrée", "E", "Zone El Waha, Ghomrassen", ProjectStatus.COMPLETED, new BigDecimal("1980000.000"), LocalDate.of(2023, 9, 5), LocalDate.of(2026, 1, 15))
        );

        Map<String, Project> projectsByCode = projectRepository.findAll().stream()
                .collect(Collectors.toMap(Project::getCode, value -> value, (a, b) -> a, HashMap::new));
        Map<String, Client> clientsByEmail = clientRepository.findAll().stream()
                .filter(client -> client.getEmail() != null)
                .collect(Collectors.toMap(Client::getEmail, value -> value, (a, b) -> a, HashMap::new));

        for (int residenceIndex = 0; residenceIndex < residences.size(); residenceIndex++) {
            DemoResidenceSpec residence = residences.get(residenceIndex);
            Project project = ensureDemoProject(residence, projectsByCode);
            List<Apartment> projectApartments = apartmentRepository.findByProjectId(project.getId());

            for (int floor = 0; floor < DEMO_RESIDENCE_FLOORS; floor++) {
                for (int unit = 1; unit <= DEMO_APARTMENTS_PER_FLOOR; unit++) {
                    int apartmentSequence = floor * DEMO_APARTMENTS_PER_FLOOR + unit;
                    int globalSequence = residenceIndex * DEMO_RESIDENCE_FLOORS * DEMO_APARTMENTS_PER_FLOOR + apartmentSequence;
                    Client client = apartmentSequence <= DEMO_CLIENTS_PER_RESIDENCE
                            ? ensureDemoClient(residence, project, globalSequence, clientsByEmail)
                            : null;
                    Apartment apartment = ensureDemoApartment(residence, project, client, floor, unit, projectApartments);

                    if (client != null && apartment.getAcquirer() != null && client.getId().equals(apartment.getAcquirer().getId())) {
                        ensureDemoPurchaseAndAdvances(residence, project, client, apartment, globalSequence);
                    }
                }
            }
        }
    }

    /**
     * Persists a demo project for the given spec when missing; otherwise returns the existing row.
     */
    private Project ensureDemoProject(DemoResidenceSpec residence, Map<String, Project> projectsByCode) {
        Project existing = projectsByCode.get(residence.code());
        if (existing != null) {
            return existing;
        }

        Project project = buildProject(
                residence.code(),
                residence.name(),
                residence.location(),
                "Données démo: résidence de 6 étages avec 4 appartements par étage, clients, achats et paiements variés.",
                residence.startDate(),
                residence.expectedEndDate(),
                residence.budget(),
                residence.status()
        );
        project.setActiveContext(false);
        Project saved = projectRepository.save(project);
        projectsByCode.put(saved.getCode(), saved);
        return saved;
    }

    /**
     * Ensures a deterministic demo client exists for the apartment slot (keyed by demo email).
     */
    private Client ensureDemoClient(DemoResidenceSpec residence, Project project, int globalSequence, Map<String, Client> clientsByEmail) {
        String email = String.format("client%03d.%s@demo-spi.tn", globalSequence, residence.block().toLowerCase());
        Client existing = clientsByEmail.get(email);
        if (existing != null) {
            return existing;
        }

        String firstName = DEMO_FIRST_NAMES[globalSequence % DEMO_FIRST_NAMES.length];
        String lastName = DEMO_LAST_NAMES[(globalSequence * 7) % DEMO_LAST_NAMES.length];
        Client client = buildClient(
                firstName + " " + lastName,
                String.format("9%07d", 1000000 + globalSequence),
                email,
                residence.location() + ", Tataouine",
                String.format("%08d", 70000000 + globalSequence),
                "Client démo rattaché à " + residence.name() + ".",
                project
        );
        Client saved = clientRepository.save(client);
        clientsByEmail.put(email, saved);
        return saved;
    }

    /**
     * Creates or updates a demo apartment; links {@code client} as acquirer when provided and not yet set.
     */
    private Apartment ensureDemoApartment(DemoResidenceSpec residence, Project project, Client client, int floor, int unit, List<Apartment> projectApartments) {
        String apartmentNumber = String.format("%s%d%d", residence.block(), floor, unit);
        Apartment existing = projectApartments.stream()
                .filter(apartment -> apartmentNumber.equals(apartment.getApartmentNumber()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            if (client != null && existing.getAcquirer() == null) {
                existing.setAcquirer(client);
                apartmentRepository.save(existing);
            }
            return existing;
        }

        DemoApartmentProfile profile = demoApartmentProfile(unit);
        BigDecimal totalSurface = profile.baseSurface()
                .add(BigDecimal.valueOf(floor * 3L + unit))
                .setScale(3, RoundingMode.HALF_UP);
        BigDecimal gardenSurface = floor == 0 ? BigDecimal.valueOf(18L + unit * 4L).setScale(3, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal squareMeterPrice = BigDecimal.valueOf(1850L + floor * 45L + unit * 35L);
        BigDecimal totalSalePrice = totalSurface.multiply(squareMeterPrice)
                .add(gardenSurface.multiply(BigDecimal.valueOf(450)))
                .setScale(3, RoundingMode.HALF_UP);
        String parking = unit >= 3 ? "P-" + apartmentNumber + " + P-" + apartmentNumber + "B" : "P-" + apartmentNumber;

        Apartment apartment = createApartment(
                apartmentNumber,
                profile.type(),
                residence.name() + " - Appartement " + profile.type() + " " + apartmentNumber + " étage " + floor,
                totalSurface,
                gardenSurface,
                parking,
                profile.cellarCount(),
                totalSalePrice,
                project.getId(),
                client != null ? client.getId() : null
        );
        projectApartments.add(apartment);
        return apartment;
    }

    /**
     * Idempotent purchase and advance lines for a demo apartment, with amounts driven by project status.
     */
    private void ensureDemoPurchaseAndAdvances(DemoResidenceSpec residence, Project project, Client client, Apartment apartment, int globalSequence) {
        String purchaseReference = "ACH-DEMO-" + residence.block() + "-" + apartment.getApartmentNumber();
        BigDecimal totalAmount = apartment.getTotalSalePrice() != null ? apartment.getTotalSalePrice() : BigDecimal.ZERO;
        BigDecimal paidAmount = totalAmount.multiply(demoDirectPaymentRatio(project.getStatus(), globalSequence)).setScale(3, RoundingMode.HALF_UP);

        if (clientPurchaseRepository.findByReference(purchaseReference).isEmpty()
                && clientPurchaseRepository.findByApartmentId(apartment.getId()).isEmpty()) {
            ClientPurchaseRequest request = new ClientPurchaseRequest();
            request.setReference(purchaseReference);
            request.setPurchaseDate(residence.startDate().plusDays(globalSequence % 180L));
            request.setContractDate(request.getPurchaseDate().plusDays(5));
            request.setAssetDescription(apartment.getDetail());
            request.setTotalAmount(totalAmount);
            request.setPaidAmount(paidAmount);
            request.setNotes("Achat démo généré automatiquement pour présentation client.");
            request.setClientId(client.getId());
            request.setProjectId(project.getId());
            request.setApartmentId(apartment.getId());
            clientPurchaseService.create(request);
        }

        BigDecimal effectivePaidAmount = clientPurchaseRepository.findByApartmentId(apartment.getId())
                .map(purchase -> purchase.getPaidAmount() != null ? purchase.getPaidAmount() : BigDecimal.ZERO)
                .orElse(paidAmount);
        BigDecimal existingAdvanceAmount = clientAdvanceRepository.findByApartmentId(apartment.getId()).stream()
                .map(advance -> advance.getAmount() != null ? advance.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> ratios = demoAdvanceRatios(project.getStatus(), globalSequence);
        for (int i = 0; i < ratios.size(); i++) {
            String advanceReference = "ACC-DEMO-" + residence.block() + "-" + apartment.getApartmentNumber() + "-" + (i + 1);
            if (clientAdvanceRepository.findByReference(advanceReference).isPresent()) {
                continue;
            }
            BigDecimal remainingAmount = totalAmount.subtract(effectivePaidAmount).subtract(existingAdvanceAmount).max(BigDecimal.ZERO);
            BigDecimal requestedAmount = totalAmount.multiply(ratios.get(i)).setScale(3, RoundingMode.HALF_UP).min(remainingAmount);
            if (requestedAmount.signum() <= 0) {
                continue;
            }

            ClientAdvanceRequest request = new ClientAdvanceRequest();
            request.setReference(advanceReference);
            request.setAdvanceDate(residence.startDate().plusDays(15L + globalSequence % 120L + i * 30L));
            request.setAmount(requestedAmount);
            request.setPaymentMethod(DEMO_PAYMENT_METHODS[(globalSequence + i) % DEMO_PAYMENT_METHODS.length]);
            request.setNotes("Paiement démo rattaché à " + apartment.getApartmentNumber() + ".");
            request.setApartmentId(apartment.getId());
            clientAdvanceService.create(request);
            existingAdvanceAmount = existingAdvanceAmount.add(requestedAmount);
        }
    }

    /** Picks apartment type, base surface, and cellar count from the unit index on a floor. */
    private DemoApartmentProfile demoApartmentProfile(int unit) {
        return switch (unit) {
            case 1 -> new DemoApartmentProfile("S+1", new BigDecimal("72.000"), 0);
            case 2 -> new DemoApartmentProfile("S+2", new BigDecimal("96.000"), 1);
            case 3 -> new DemoApartmentProfile("S+3", new BigDecimal("124.000"), 1);
            default -> new DemoApartmentProfile("S+4", new BigDecimal("148.000"), 2);
        };
    }

    /** Share of sale price recorded as direct payment on the purchase, for demo data only. */
    private BigDecimal demoDirectPaymentRatio(ProjectStatus status, int sequence) {
        if (status == ProjectStatus.COMPLETED) {
            return switch (sequence % 4) {
                case 0 -> new BigDecimal("0.750");
                case 1 -> new BigDecimal("0.850");
                case 2 -> new BigDecimal("0.600");
                default -> new BigDecimal("0.500");
            };
        }
        if (status == ProjectStatus.PLANNED) {
            return switch (sequence % 4) {
                case 0 -> BigDecimal.ZERO;
                case 1 -> new BigDecimal("0.050");
                case 2 -> new BigDecimal("0.100");
                default -> new BigDecimal("0.150");
            };
        }
        return switch (sequence % 5) {
            case 0 -> new BigDecimal("0.200");
            case 1 -> new BigDecimal("0.350");
            case 2 -> new BigDecimal("0.100");
            case 3 -> new BigDecimal("0.500");
            default -> BigDecimal.ZERO;
        };
    }

    /** Ratios of total price used to generate client advance lines (demo). */
    private List<BigDecimal> demoAdvanceRatios(ProjectStatus status, int sequence) {
        if (status == ProjectStatus.COMPLETED) {
            return List.of(BigDecimal.ONE.subtract(demoDirectPaymentRatio(status, sequence)));
        }
        if (status == ProjectStatus.PLANNED) {
            return sequence % 3 == 0 ? List.of() : List.of(new BigDecimal("0.050"));
        }
        return switch (sequence % 6) {
            case 0 -> List.of(new BigDecimal("0.100"));
            case 1 -> List.of(new BigDecimal("0.150"), new BigDecimal("0.100"));
            case 2 -> List.of(new BigDecimal("0.200"));
            case 3 -> List.of();
            case 4 -> List.of(new BigDecimal("0.250"));
            default -> List.of(new BigDecimal("0.120"), new BigDecimal("0.080"));
        };
    }

    /** Creates the fixed demo supplier set and returns them keyed by legal name. */
    private Map<String, Supplier> seedSuppliers(Map<String, SupplierTypeOption> supplierTypes) {
        Supplier materials = buildSupplier("Comptoir des Matériaux du Sud", "MF-1289456/A/M/000", "75200110", "contact@cmsud.tn", "Zone industrielle, Médenine", supplierTypes.get("Fournisseur"));
        Supplier contractor = buildSupplier("Entreprise Bâtir Ghomrassen", "MF-1297754/B/M/000", "75311422", "direction@batir-ghomrassen.tn", "Rue Habib Bourguiba, Ghomrassen", supplierTypes.get("Entrepreneur"));
        Supplier engineer = buildSupplier("Bureau d'Études Ingénierie Tataouine", "MF-1308821/C/M/000", "75420218", "etudes@beit.tn", "Avenue Farhat Hached, Tataouine", supplierTypes.get("Ingénieur"));
        Supplier architect = buildSupplier("Atelier Architecture El Medina", "MF-1314420/D/M/000", "75600991", "contact@atelier-medina.tn", "Centre urbain nord, Tunis", supplierTypes.get("Architecte"));
        Supplier services = buildSupplier("Services Administratifs du Sud", "MF-1320045/E/M/000", "75188900", "admin@sud-services.tn", "Gabès route de Médenine", supplierTypes.get("Autre"));
        Supplier notary = buildSupplier("Étude Notariale Khmiri", "MF-1331160/F/M/000", "71220191", "contact@notaire-khmiri.tn", "Ariana centre", supplierTypes.get("Autre"));
        Supplier electricity = buildSupplier("Équipements Électriques Sahara", "MF-1345588/G/M/000", "75899110", "sales@eesahara.tn", "Zone artisanale Tataouine", supplierTypes.get("Fournisseur"));

        List<Supplier> saved = supplierRepository.saveAll(List.of(materials, contractor, engineer, architect, services, notary, electricity));
        return saved.stream().collect(Collectors.toMap(Supplier::getName, value -> value));
    }

    /** Builds a transient {@link Supplier} entity for batch persistence. */
    private Supplier buildSupplier(String name, String fiscalId, String phone, String email, String address, SupplierTypeOption type) {
        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setFiscalId(fiscalId);
        supplier.setPhone(phone);
        supplier.setEmail(email);
        supplier.setAddress(address);
        supplier.setType(type);
        supplier.setActive(true);
        return supplier;
    }

    /** Seeds the six narrative demo clients keyed by full name. */
    private Map<String, Client> seedClients(Map<String, Project> projects) {
        Client c1 = buildClient("Sami Ben Youssef", "98111222", "sami.benyoussef@email.tn", "Ghomrassen centre, Tataouine", "09876543", "Client intéressé par un appartement S+2.", projects.get("Résidence Oasis Ghomrassen"));
        Client c2 = buildClient("Imen Trabelsi", "96700331", "imen.trabelsi@email.tn", "Medenine ville", "11223344", "Préférence pour une villa avec jardin.", projects.get("Villas El Waha"));
        Client c3 = buildClient("Mohamed Gharbi", "95333777", "mgharbi@email.tn", "Tataouine nouvelle", "22334455", "Paiement par virements successifs.", projects.get("Immeuble Jasmin"));
        Client c4 = buildClient("Nadia Kchaou", "94445001", "nadia.kchaou@email.tn", "Sfax route de Tunis", "33445566", "Dossier en cours de finalisation bancaire.", projects.get("Résidence Oasis Ghomrassen"));
        Client c5 = buildClient("Marwen Dammak", "92550110", "marwen.dammak@email.tn", "Gabès centre", "44556677", "Cherche un local commercial pour investissement.", projects.get("Bureaux Les Palmes"));
        Client c6 = buildClient("Rim Bouzid", "93678210", "rim.bouzid@email.tn", "Ben Guerdane", "55667788", "Souhaite une villa livrable rapidement.", projects.get("Villas El Waha"));

        List<Client> saved = clientRepository.saveAll(List.of(c1, c2, c3, c4, c5, c6));
        return saved.stream().collect(Collectors.toMap(Client::getFullName, value -> value));
    }

    /** Builds a transient {@link Client} entity for batch persistence. */
    private Client buildClient(String fullName, String phone, String email, String address, String cin, String notes, Project project) {
        Client client = new Client();
        client.setFullName(fullName);
        client.setPhone(phone);
        client.setEmail(email);
        client.setAddress(address);
        client.setCinOrFiscalId(cin);
        client.setNotes(notes);
        client.setActive(true);
        client.setProject(project);
        return client;
    }

    /** Seeds the four main Ghomrassen projects keyed by display name. */
    private Map<String, Project> seedProjects() {
        Project p1 = buildProject("SPI-GHOM-RES-01", "Résidence Oasis Ghomrassen", "Ghomrassen, Tataouine", "Projet résidentiel principal de SP Immobilière GHOMRASSEN avec appartements S+2 et S+3.", LocalDate.of(2025, 10, 1), LocalDate.of(2027, 3, 31), new BigDecimal("1250000.000"), ProjectStatus.IN_PROGRESS);
        Project p2 = buildProject("SPI-GHOM-COM-02", "Immeuble Jasmin", "Centre-ville Ghomrassen", "Immeuble à usage mixte commerce et habitation.", LocalDate.of(2026, 2, 1), LocalDate.of(2027, 12, 15), new BigDecimal("890000.000"), ProjectStatus.PLANNED);
        Project p3 = buildProject("SPI-GHOM-VIL-03", "Villas El Waha", "Zone résidentielle El Waha, Ghomrassen", "Programme de villas individuelles livré en plusieurs tranches.", LocalDate.of(2024, 4, 10), LocalDate.of(2025, 12, 20), new BigDecimal("640000.000"), ProjectStatus.COMPLETED);
        Project p4 = buildProject("SPI-GHOM-BUR-04", "Bureaux Les Palmes", "Entrée Ghomrassen", "Petit ensemble de bureaux et commerces de proximité.", LocalDate.of(2026, 1, 12), LocalDate.of(2027, 8, 30), new BigDecimal("520000.000"), ProjectStatus.IN_PROGRESS);

        List<Project> saved = projectRepository.saveAll(List.of(p1, p2, p3, p4));
        return saved.stream().collect(Collectors.toMap(Project::getName, value -> value));
    }

    /** Builds a transient {@link Project} entity; marks {@code SPI-GHOM-RES-01} as active context. */
    private Project buildProject(String code, String name, String location, String description, LocalDate startDate, LocalDate expectedEndDate, BigDecimal budget, ProjectStatus status) {
        Project project = new Project();
        project.setCode(code);
        project.setName(name);
        project.setLocation(location);
        project.setDescription(description);
        project.setStartDate(startDate);
        project.setExpectedEndDate(expectedEndDate);
        project.setBudget(budget);
        project.setStatus(status);
        if ("SPI-GHOM-RES-01".equals(code)) {
            project.setActiveContext(true);
        }
        return project;
    }

    /**
     * Seeds sample apartments for the narrative clients (linked to seeded projects and acquirers).
     */
    private void seedApartments(Map<String, Client> clients, Map<String, Project> projects) {
        createApartment("A12", "S+2", "Appartement S+2 A-12 - 2ème étage", new BigDecimal("108.000"), BigDecimal.ZERO, "P1", 1, new BigDecimal("185000.000"), projects.get("Résidence Oasis Ghomrassen").getId(), clients.get("Sami Ben Youssef").getId());
        createApartment("V2", "S+4", "Villa V2 avec jardin", new BigDecimal("240.000"), new BigDecimal("80.000"), "P1 + P2", 1, new BigDecimal("310000.000"), projects.get("Villas El Waha").getId(), clients.get("Imen Trabelsi").getId());
        createApartment("RDC03", "LOCAL", "Local commercial RDC-03", new BigDecimal("95.000"), BigDecimal.ZERO, "", 1, new BigDecimal("240000.000"), projects.get("Immeuble Jasmin").getId(), clients.get("Mohamed Gharbi").getId());
        createApartment("B07", "S+2", "Appartement B-07 vue jardin", new BigDecimal("112.000"), new BigDecimal("20.000"), "P2", 1, new BigDecimal("198000.000"), projects.get("Résidence Oasis Ghomrassen").getId(), clients.get("Nadia Kchaou").getId());
        createApartment("B04", "BUREAU", "Bureau B-04 avec parking", new BigDecimal("88.000"), BigDecimal.ZERO, "Parking sous-sol 01", 0, new BigDecimal("145000.000"), projects.get("Bureaux Les Palmes").getId(), clients.get("Marwen Dammak").getId());
        createApartment("C4", "S+4", "Villa C4 avec jardin", new BigDecimal("230.000"), new BigDecimal("90.000"), "P1 + P2", 1, new BigDecimal("295000.000"), projects.get("Villas El Waha").getId(), clients.get("Rim Bouzid").getId());
    }

    /** Persists an apartment through {@link ApartmentService}. */
    private Apartment createApartment(String number, String type, String detail, BigDecimal totalSurface, BigDecimal gardenSurface, String parkingCount, Integer cellarCount, BigDecimal totalSalePrice, Long projectId, Long acquirerId) {
        ApartmentRequest request = new ApartmentRequest();
        request.setApartmentNumber(number);
        request.setApartmentType(type);
        request.setDetail(detail);
        request.setTotalSurface(totalSurface);
        request.setGardenSurface(gardenSurface);
        request.setParkingCount(parkingCount);
        request.setCellarCount(cellarCount);
        request.setTotalSalePrice(totalSalePrice);
        request.setProjectId(projectId);
        request.setAcquirerId(acquirerId);
        return apartmentService.create(request);
    }

    /** Inserts the fixed demo expense rows for the main projects. */
    private void seedExpenses(Map<String, ExpenseCategory> categories, Map<String, Supplier> suppliers, Map<String, Project> projects) {
        createExpense("DEP-2026-001", LocalDate.of(2026, 3, 3), "Achat ciment et ferraillage tranche A", new BigDecimal("18500.000"), new BigDecimal("3515.000"), new BigDecimal("22015.000"), PaymentMethod.BANK_TRANSFER, "FAC-CMS-2026-031", "Approvisionnement principal chantier mars 2026.", categories.get("Frais Fournisseurs").getId(), projects.get("Résidence Oasis Ghomrassen").getId(), suppliers.get("Comptoir des Matériaux du Sud").getId());
        createExpense("DEP-2026-002", LocalDate.of(2026, 3, 7), "Situation travaux gros oeuvre lot B", new BigDecimal("42000.000"), new BigDecimal("7980.000"), new BigDecimal("49980.000"), PaymentMethod.CHECK, "SIT-BG-2026-007", "Situation mensuelle entrepreneur, lot B.", categories.get("Frais Fournisseurs").getId(), projects.get("Résidence Oasis Ghomrassen").getId(), suppliers.get("Entreprise Bâtir Ghomrassen").getId());
        createExpense("DEP-2026-003", LocalDate.of(2026, 3, 15), "Honoraires étude béton armé", new BigDecimal("6500.000"), new BigDecimal("1235.000"), new BigDecimal("7735.000"), PaymentMethod.BANK_TRANSFER, "BEIT-2026-014", "Étude structure bâtiment principal.", categories.get("Frais Ingénieurs").getId(), projects.get("Résidence Oasis Ghomrassen").getId(), suppliers.get("Bureau d'Études Ingénierie Tataouine").getId());
        createExpense("DEP-2026-004", LocalDate.of(2026, 3, 20), "Frais dépôt dossier municipal", new BigDecimal("1200.000"), BigDecimal.ZERO, new BigDecimal("1200.000"), PaymentMethod.CASH, "BAL-2026-089", "Paiement municipalité Ghomrassen.", categories.get("Frais Baladiya").getId(), projects.get("Immeuble Jasmin").getId(), null);
        createExpense("DEP-2026-005", LocalDate.of(2026, 4, 2), "Esquisses architecturales phase APS", new BigDecimal("4800.000"), new BigDecimal("912.000"), new BigDecimal("5712.000"), PaymentMethod.BANK_TRANSFER, "ATM-APS-2026-005", "Études préliminaires immeuble Jasmin.", categories.get("Autres").getId(), projects.get("Immeuble Jasmin").getId(), suppliers.get("Atelier Architecture El Medina").getId());
        createExpense("DEP-2026-006", LocalDate.of(2025, 11, 18), "Frais administratifs clôture dossier villa B3", new BigDecimal("950.000"), new BigDecimal("180.500"), new BigDecimal("1130.500"), PaymentMethod.CARD, "SAS-2025-221", "Documents et timbres administratifs.", categories.get("Frais Administratifs").getId(), projects.get("Villas El Waha").getId(), suppliers.get("Services Administratifs du Sud").getId());
        createExpense("DEP-2026-007", LocalDate.of(2026, 4, 6), "Acte notarié réservation lot C4", new BigDecimal("2200.000"), new BigDecimal("418.000"), new BigDecimal("2618.000"), PaymentMethod.BANK_TRANSFER, "NOT-2026-044", "Frais notariés sur réservation villa.", categories.get("Frais Notaire").getId(), projects.get("Villas El Waha").getId(), suppliers.get("Étude Notariale Khmiri").getId());
        createExpense("DEP-2026-008", LocalDate.of(2026, 4, 11), "Tableaux électriques bureaux RDC", new BigDecimal("9800.000"), new BigDecimal("1862.000"), new BigDecimal("11662.000"), PaymentMethod.CHECK, "EES-2026-073", "Achat matériel électrique bloc B.", categories.get("Frais Fournisseurs").getId(), projects.get("Bureaux Les Palmes").getId(), suppliers.get("Équipements Électriques Sahara").getId());
        createExpense("DEP-2026-009", LocalDate.of(2026, 4, 15), "Deuxième situation entrepreneur lot B", new BigDecimal("36000.000"), new BigDecimal("6840.000"), new BigDecimal("42840.000"), PaymentMethod.BANK_TRANSFER, "SIT-BG-2026-010", "Travaux maçonnerie et coffrage.", categories.get("Frais Fournisseurs").getId(), projects.get("Résidence Oasis Ghomrassen").getId(), suppliers.get("Entreprise Bâtir Ghomrassen").getId());
        createExpense("DEP-2026-010", LocalDate.of(2026, 4, 18), "Mission suivi technique chantier", new BigDecimal("3200.000"), new BigDecimal("608.000"), new BigDecimal("3808.000"), PaymentMethod.BANK_TRANSFER, "BEIT-2026-019", "Visites et rapport technique d'avancement.", categories.get("Frais Ingénieurs").getId(), projects.get("Bureaux Les Palmes").getId(), suppliers.get("Bureau d'Études Ingénierie Tataouine").getId());
        createExpense("DEP-2026-011", LocalDate.of(2026, 4, 20), "Fournitures administratives siège", new BigDecimal("680.000"), new BigDecimal("129.200"), new BigDecimal("809.200"), PaymentMethod.CARD, "ADM-2026-118", "Papeterie et fournitures de bureau.", categories.get("Frais Administratifs").getId(), projects.get("Immeuble Jasmin").getId(), suppliers.get("Services Administratifs du Sud").getId());
        createExpense("DEP-2026-012", LocalDate.of(2026, 4, 23), "Levés architecturaux complémentaires", new BigDecimal("2600.000"), new BigDecimal("494.000"), new BigDecimal("3094.000"), PaymentMethod.BANK_TRANSFER, "ATM-APS-2026-012", "Compléments plans façade et circulation.", categories.get("Autres").getId(), projects.get("Immeuble Jasmin").getId(), suppliers.get("Atelier Architecture El Medina").getId());
    }

    /** Seeds client advances tied to seeded apartments (resolved by client and project). */
    private void seedClientAdvances(Map<String, Client> clients, Map<String, Project> projects) {
        createAdvance("ACC-2026-001", LocalDate.of(2026, 2, 10), new BigDecimal("15000.000"), PaymentMethod.BANK_TRANSFER, "Réservation appartement A-12.", clients.get("Sami Ben Youssef").getId(), projects.get("Résidence Oasis Ghomrassen").getId());
        createAdvance("ACC-2026-002", LocalDate.of(2026, 2, 22), new BigDecimal("22000.000"), PaymentMethod.CHECK, "Premier acompte villa V2.", clients.get("Imen Trabelsi").getId(), projects.get("Villas El Waha").getId());
        createAdvance("ACC-2026-003", LocalDate.of(2026, 3, 12), new BigDecimal("18000.000"), PaymentMethod.BANK_TRANSFER, "Acompte local commercial RDC-03.", clients.get("Mohamed Gharbi").getId(), projects.get("Immeuble Jasmin").getId());
        createAdvance("ACC-2026-004", LocalDate.of(2026, 4, 5), new BigDecimal("12000.000"), PaymentMethod.CASH, "Versement initial dossier appartement B-07.", clients.get("Nadia Kchaou").getId(), projects.get("Résidence Oasis Ghomrassen").getId());
        createAdvance("ACC-2026-005", LocalDate.of(2026, 4, 9), new BigDecimal("25000.000"), PaymentMethod.BANK_TRANSFER, "Réservation bureau B-04.", clients.get("Marwen Dammak").getId(), projects.get("Bureaux Les Palmes").getId());
        createAdvance("ACC-2026-006", LocalDate.of(2026, 4, 21), new BigDecimal("35000.000"), PaymentMethod.CHECK, "Deuxième acompte villa C4.", clients.get("Rim Bouzid").getId(), projects.get("Villas El Waha").getId());
    }

    /** Seeds client purchases for the narrative demo lots. */
    private void seedClientPurchases(Map<String, Client> clients, Map<String, Project> projects) {
        createPurchase("ACH-2026-001", LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 14), "Appartement S+2 A-12 - 2ème étage", new BigDecimal("185000.000"), new BigDecimal("5000.000"), "Paiement échelonné sur 18 mois.", clients.get("Sami Ben Youssef").getId(), projects.get("Résidence Oasis Ghomrassen").getId());
        createPurchase("ACH-2026-002", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 6), "Local commercial RDC-03", new BigDecimal("240000.000"), BigDecimal.ZERO, "Livraison prévue après achèvement du gros oeuvre.", clients.get("Mohamed Gharbi").getId(), projects.get("Immeuble Jasmin").getId());
        createPurchase("ACH-2025-010", LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 7), "Villa V2 avec jardin", new BigDecimal("310000.000"), new BigDecimal("288000.000"), "Dossier presque soldé.", clients.get("Imen Trabelsi").getId(), projects.get("Villas El Waha").getId());
        createPurchase("ACH-2026-003", LocalDate.of(2026, 4, 8), LocalDate.of(2026, 4, 10), "Appartement B-07 vue jardin", new BigDecimal("198000.000"), new BigDecimal("38000.000"), "Client en attente du crédit logement.", clients.get("Nadia Kchaou").getId(), projects.get("Résidence Oasis Ghomrassen").getId());
        createPurchase("ACH-2026-004", LocalDate.of(2026, 4, 12), LocalDate.of(2026, 4, 15), "Bureau B-04 avec parking", new BigDecimal("145000.000"), new BigDecimal("50000.000"), "Usage investissement locatif.", clients.get("Marwen Dammak").getId(), projects.get("Bureaux Les Palmes").getId());
        createPurchase("ACH-2026-005", LocalDate.of(2026, 4, 18), LocalDate.of(2026, 4, 22), "Villa C4 avec jardin", new BigDecimal("295000.000"), new BigDecimal("260000.000"), "Paiement mixte bancaire et fonds propres.", clients.get("Rim Bouzid").getId(), projects.get("Villas El Waha").getId());
    }

    /** Maps parameters to an {@link ExpenseRequest} and persists via {@link ExpenseService}. */
    private void createExpense(String reference, LocalDate date, String description, BigDecimal amountHt, BigDecimal vatAmount, BigDecimal amountTtc, PaymentMethod paymentMethod, String documentNumber, String notes, Long categoryId, Long projectId, Long supplierId) {
        ExpenseRequest request = new ExpenseRequest();
        request.setReference(reference);
        request.setExpenseDate(date);
        request.setDescription(description);
        request.setAmountHt(amountHt);
        // Le taux est deduit du couple HT / TVA des donnees de demonstration, pour que les
        // montants generes restent identiques a ceux d'avant CALC-01.
        request.setVatRate(vatAmount.divide(amountHt, 4, RoundingMode.HALF_UP));
        request.setVatAmount(vatAmount);
        request.setAmountTtc(amountTtc);
        request.setPaymentMethod(paymentMethod);
        request.setDocumentNumber(documentNumber);
        request.setNotes(notes);
        request.setCategoryId(categoryId);
        request.setProjectId(projectId);
        request.setSupplierId(supplierId);
        expenseService.create(request);
    }

    /** Creates a client advance for the apartment matching the client and project in seeded data. */
    private void createAdvance(String reference, LocalDate date, BigDecimal amount, PaymentMethod paymentMethod, String notes, Long clientId, Long projectId) {
        ClientAdvanceRequest request = new ClientAdvanceRequest();
        request.setReference(reference);
        request.setAdvanceDate(date);
        request.setAmount(amount);
        request.setPaymentMethod(paymentMethod);
        request.setNotes(notes);
        request.setApartmentId(findApartmentIdForSeed(clientId, projectId));
        clientAdvanceService.create(request);
    }

    /**
     * Locates the first apartment for the given acquirer and project (used after {@link #seedApartments}).
     *
     * @throws IllegalStateException when no matching seeded apartment exists
     */
    private Long findApartmentIdForSeed(Long clientId, Long projectId) {
        return apartmentService.findAll(Pageable.unpaged()).stream()
                .filter(apartment -> apartment.getProject() != null && apartment.getAcquirer() != null)
                .filter(apartment -> apartment.getProject().getId().equals(projectId) && apartment.getAcquirer().getId().equals(clientId))
                .map(Apartment::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No apartment seed found for client " + clientId + " and project " + projectId));
    }

    /** Creates a client purchase for the apartment matching the client and project in seeded data. */
    private void createPurchase(String reference, LocalDate purchaseDate, LocalDate contractDate, String assetDescription, BigDecimal totalAmount, BigDecimal paidAmount, String notes, Long clientId, Long projectId) {
        ClientPurchaseRequest request = new ClientPurchaseRequest();
        request.setReference(reference);
        request.setPurchaseDate(purchaseDate);
        request.setContractDate(contractDate);
        request.setAssetDescription(assetDescription);
        request.setTotalAmount(totalAmount);
        request.setPaidAmount(paidAmount);
        request.setNotes(notes);
        request.setClientId(clientId);
        request.setProjectId(projectId);
        request.setApartmentId(findApartmentIdForSeed(clientId, projectId));
        clientPurchaseService.create(request);
    }

    /** Static definition of one SPI demo residence (code, marketing data, budget, dates). */
    private record DemoResidenceSpec(
            String code,
            String name,
            String block,
            String location,
            ProjectStatus status,
            BigDecimal budget,
            LocalDate startDate,
            LocalDate expectedEndDate
    ) {
    }

    /** Typology and surfaces for a generated demo apartment on one floor. */
    private record DemoApartmentProfile(String type, BigDecimal baseSurface, Integer cellarCount) {
    }
}
