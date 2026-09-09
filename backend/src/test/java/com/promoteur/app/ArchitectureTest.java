package com.promoteur.app;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * Turns the non-negotiable rules of {@code CLAUDE.md} into checks a build can fail on.
 *
 * <p>Those rules were held by discipline alone: the layering, the DTO boundary, «&nbsp;money is
 * always a {@code BigDecimal} at scale 3&nbsp;», read-only finders. Discipline works until the
 * day someone new adds a {@code double} field or returns an entity from a controller, and no
 * test says anything.</p>
 *
 * <h2>Layers are recognised by name, not by package</h2>
 *
 * <p>The code is laid out by feature — {@code expense}, {@code client}, {@code apartment}… — so
 * a package name no longer says which layer a class belongs to. The rules therefore identify a
 * layer the way a reader does: {@code @RestController} for the web edge, {@code *ServiceImpl}
 * for the implementations, anything assignable to Spring Data's {@code Repository} for
 * persistence, {@code @Entity} for the mapped types, {@code *Response} for the outgoing DTOs.
 * The <em>dependency</em> discipline being checked is unchanged; only how a layer is spotted.</p>
 *
 * <h2>What the feature layout cost</h2>
 *
 * <p>The package-cycle rule is gone. Between features, cycles are the normal state of this
 * domain — a contract references a lot, and the sales board reads contracts — and the rule
 * reported more than a hundred of them, none actionable. What replaced it is the invariant a
 * feature layout really has and which is worth defending:
 * {@link #sharedDoesNotDependOnAFeature}.</p>
 *
 * <p><b>On {@code @DisplayName}</b>: unlike everywhere else in this suite, the rules below carry
 * no {@code @DisplayName}. An {@code @ArchTest} is a {@code static final ArchRule} field, not a
 * method, so the annotation does not apply; the sentence goes to {@code as(...)} on the rule
 * itself, which is what appears in the report.</p>
 */
@AnalyzeClasses(packages = "com.promoteur.app", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String SHARED = "com.promoteur.app.shared";

    // ---------------------------------------------------------------- layers

    @ArchTest
    static final ArchRule nothingDependsOnAController = noClasses()
            .that().areNotAnnotatedWith(RestController.class)
            .should().dependOnClassesThat(areControllers())
            .as("nothing depends on a controller: the web edge is an entry point, not a service");

    /**
     * Repositories are excluded from the subjects, not from the targets: every one of them
     * extends {@code JpaRepository} and would otherwise report itself as reaching a repository.
     * The two {@code CommandLineRunner} seeds of {@code config} are excluded too — CLAUDE.md
     * describes their direct repository access as intended.
     */
    @ArchTest
    static final ArchRule onlyServiceImplementationsReachRepositories = noClasses()
            .that(areNotServiceImplementations())
            .and(areNotStartUpSeeds())
            .and(DescribedPredicate.not(areRepositories()))
            .should().dependOnClassesThat(areRepositories())
            .as("only service implementations reach repositories");

    @ArchTest
    static final ArchRule noControllerDependsOnARepository = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat(areRepositories())
            .as("no controller depends on a repository");

    @ArchTest
    static final ArchRule noControllerDependsOnAServiceImplementation = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat(haveSimpleNameEndingWith("ServiceImpl"))
            .as("no controller depends on a service implementation, only on its interface");

    @ArchTest
    static final ArchRule everyServiceImplementationImplementsItsInterface = classes()
            .that().haveSimpleNameEndingWith("ServiceImpl")
            .should(implementAnInterfaceNamedLikeTheImplementation())
            .as("every *ServiceImpl implements the matching *Service interface");

    // ------------------------------------------------- the feature boundary

    /**
     * The one structural invariant a feature layout really has: {@code shared} is the bottom of
     * the graph. A feature may use {@code shared}; {@code shared} using a feature turns the
     * dependency around and makes the package a dumping ground.
     *
     * <p>One exclusion, and it is a genuine smell rather than a legitimate case:
     * {@code ReferenceGeneratorServiceImpl} lives in {@code shared} but reads
     * {@code ExpenseRepository}, {@code ClientAdvanceRepository} and
     * {@code ClientPurchaseRepository} to check that a drawn reference is free. The fix is to
     * invert it — let each caller pass the «&nbsp;is this one taken&nbsp;» check in — which is a
     * production change nobody has decided on yet. Reported, not smuggled.</p>
     */
    @ArchTest
    static final ArchRule sharedDoesNotDependOnAFeature = noClasses()
            .that().resideInAPackage(SHARED)
            .and(areNotTheReferenceGenerator())
            .should().dependOnClassesThat(areInAFeaturePackage())
            .as("nothing in shared depends on a feature package");

    // ------------------------------------------------------------ DTO boundary

    @ArchTest
    static final ArchRule noControllerMethodReturnsAnEntity = noMethods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and().arePublic()
            .should(returnAJpaEntity())
            .as("no controller method returns a JPA entity, "
                    + "directly or wrapped in ResponseEntity, Page or List");

    @ArchTest
    static final ArchRule noDtoExposesAnEntity = noFields()
            .that().areDeclaredInClassesThat(areDtos())
            .should().haveRawType(areEntities())
            .as("no DTO exposes a JPA entity in a field or a record component");

    @ArchTest
    static final ArchRule everyResponseDtoIsARecord = classes()
            .that().haveSimpleNameEndingWith("Response")
            .should(beRecords())
            .as("every response DTO is a record");

    // -------------------------------------------------------------- the money rule

    /**
     * {@code DocumentServiceImpl.MARGIN} is a page margin in points, handed to OpenPDF whose API takes
     * floats. It is page geometry, not an amount, so it is excluded by name rather than by
     * loosening the rule — the next {@code float} to appear will still fail.
     */
    @ArchTest
    static final ArchRule noFieldIsADoubleOrAFloat = noFields()
            .that(areNotPageGeometry())
            .should().haveRawType(double.class)
            .orShould().haveRawType(float.class)
            .orShould().haveRawType(Double.class)
            .orShould().haveRawType(Float.class)
            .as("no field anywhere is a double or a float: money is a BigDecimal (CALC-01)");

    @ArchTest
    static final ArchRule noMethodReturnsADoubleOrAFloat = noMethods()
            .should().haveRawReturnType(double.class)
            .orShould().haveRawReturnType(float.class)
            .orShould().haveRawReturnType(Double.class)
            .orShould().haveRawReturnType(Float.class)
            .as("no method anywhere returns a double or a float");

    @ArchTest
    static final ArchRule everyMoneyColumnDeclaresPrecisionAndScale = fields()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
            .and().haveRawType(BigDecimal.class)
            .should(declarePrecisionNineteenAndScaleThree())
            .as("every BigDecimal column declares precision 19 and scale 3, "
                    + "or the scale a rate needs");

    // ------------------------------------------------- transactions and reads

    /**
     * {@code MessageServiceImpl.get} resolves a label from a resource bundle and touches no
     * database; opening a transaction for it would be wrong, not merely useless. Excluded by
     * name, so any other unannotated finder still fails.
     */
    @ArchTest
    static final ArchRule everyFinderIsReadOnlyTransactional = methods()
            .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("ServiceImpl")
            .and().arePublic()
            .and(haveAFinderName())
            .and(doNotOnlyReadTheMessageCatalogue())
            .should(beAnnotatedAsReadOnlyTransactional())
            .as("every read-only finder is annotated transactional read-only");

    @ArchTest
    static final ArchRule noServiceCallsFindAllWithoutArguments = noClasses()
            .that().haveSimpleNameEndingWith("ServiceImpl")
            .should(callFindAllWithoutArguments())
            .as("no service calls findAll() without arguments: "
                    + "a list is filtered and paged in SQL, never regrouped in Java");

    // ------------------------------------------------------------- hygiene

    @ArchTest
    static final ArchRule noClassUsesTheLegacyDateApi = noClasses()
            .should().dependOnClassesThat().haveFullyQualifiedName("java.util.Date")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.Calendar")
            .as("no class uses java.util.Date or Calendar: java.time only");

    @ArchTest
    static final ArchRule nothingPrintsToTheConsole = noClasses()
            .should().accessField(System.class, "out")
            .orShould().accessField(System.class, "err")
            .as("nothing prints to System.out or System.err");

    @ArchTest
    static final ArchRule noFieldInjection = noFields()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .as("no field injection: @RequiredArgsConstructor is the pattern of this repository");

    // ---------------------------------------------------------------- predicates

    private static DescribedPredicate<JavaClass> areControllers() {
        return new DescribedPredicate<>("are controllers") {
            @Override
            public boolean test(final JavaClass javaClass) {
                return javaClass.isAnnotatedWith(RestController.class);
            }
        };
    }

    private static DescribedPredicate<JavaClass> areRepositories() {
        return new DescribedPredicate<>("are Spring Data repositories") {
            @Override
            public boolean test(final JavaClass javaClass) {
                return javaClass.isAssignableTo(Repository.class);
            }
        };
    }

    private static DescribedPredicate<JavaClass> areEntities() {
        return new DescribedPredicate<>("are JPA entities") {
            @Override
            public boolean test(final JavaClass javaClass) {
                return javaClass.isAnnotatedWith(Entity.class);
            }
        };
    }

    /** A request or a response record: the two shapes that cross the HTTP boundary. */
    private static DescribedPredicate<JavaClass> areDtos() {
        return new DescribedPredicate<>("are DTOs") {
            @Override
            public boolean test(final JavaClass javaClass) {
                return javaClass.getSimpleName().endsWith("Request")
                        || javaClass.getSimpleName().endsWith("Response");
            }
        };
    }

    /** Everything under com.promoteur.app that is neither shared nor infrastructure. */
    private static DescribedPredicate<JavaClass> areInAFeaturePackage() {
        return new DescribedPredicate<>("are in a feature package") {
            @Override
            public boolean test(final JavaClass javaClass) {
                final String pkg = javaClass.getPackageName();
                return pkg.startsWith("com.promoteur.app.")
                        && !pkg.equals(SHARED)
                        && !pkg.equals("com.promoteur.app.config")
                        && !pkg.equals("com.promoteur.app.exception");
            }
        };
    }

    private static DescribedPredicate<JavaClass> haveSimpleNameEndingWith(final String suffix) {
        return new DescribedPredicate<>("have simple name ending with " + suffix) {
            @Override
            public boolean test(final JavaClass javaClass) {
                return javaClass.getSimpleName().endsWith(suffix);
            }
        };
    }

    private static DescribedPredicate<JavaClass> areNotServiceImplementations() {
        return new DescribedPredicate<>("are not service implementations") {
            @Override
            public boolean test(final JavaClass javaClass) {
                return !javaClass.getSimpleName().endsWith("ServiceImpl");
            }
        };
    }

    /**
     * The two {@code CommandLineRunner} seeds of {@code config} inject repositories directly,
     * which CLAUDE.md describes as intended: the reference seed creates only what is missing,
     * and the demo seed goes through the services for everything the business rules cover.
     */
    private static DescribedPredicate<JavaClass> areNotStartUpSeeds() {
        return new DescribedPredicate<>("are not the start-up seeds") {
            @Override
            public boolean test(final JavaClass javaClass) {
                return !javaClass.getSimpleName().endsWith("DataInitializer");
            }
        };
    }

    private static DescribedPredicate<JavaClass> areNotTheReferenceGenerator() {
        return new DescribedPredicate<>("are not the reference generator") {
            @Override
            public boolean test(final JavaClass javaClass) {
                return !javaClass.getSimpleName().equals("ReferenceGeneratorServiceImpl");
            }
        };
    }

    private static DescribedPredicate<JavaField> areNotPageGeometry() {
        return new DescribedPredicate<>("are not page geometry") {
            @Override
            public boolean test(final JavaField field) {
                return !"com.promoteur.app.document.DocumentServiceImpl.MARGIN".equals(field.getFullName());
            }
        };
    }

    private static DescribedPredicate<JavaMethod> haveAFinderName() {
        return new DescribedPredicate<>("have a finder name") {
            @Override
            public boolean test(final JavaMethod method) {
                final String name = method.getName();
                return name.startsWith("find") || name.startsWith("get") || name.startsWith("list")
                        || name.startsWith("search");
            }
        };
    }

    private static DescribedPredicate<JavaMethod> doNotOnlyReadTheMessageCatalogue() {
        return new DescribedPredicate<>("do not only read the message catalogue") {
            @Override
            public boolean test(final JavaMethod method) {
                return !method.getOwner().getSimpleName().equals("MessageServiceImpl");
            }
        };
    }

    // ---------------------------------------------------------------- conditions

    private static ArchCondition<JavaClass> implementAnInterfaceNamedLikeTheImplementation() {
        return new ArchCondition<>("implement the matching *Service interface") {
            @Override
            public void check(final JavaClass javaClass, final ConditionEvents events) {
                final String expected = javaClass.getSimpleName().replaceFirst("Impl$", "");
                final boolean implemented = javaClass.getRawInterfaces().stream()
                        .anyMatch(candidate -> candidate.getSimpleName().equals(expected));
                events.add(new SimpleConditionEvent(javaClass, implemented,
                        javaClass.getName() + (implemented ? " implements " : " does not implement ")
                                + expected));
            }
        };
    }

    private static ArchCondition<JavaMethod> returnAJpaEntity() {
        return new ArchCondition<>("return a JPA entity") {
            @Override
            public void check(final JavaMethod method, final ConditionEvents events) {
                final Set<String> entities = erasuresOf(method.getReturnType()).stream()
                        .filter(type -> type.isAnnotatedWith(Entity.class))
                        .map(JavaClass::getSimpleName)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                events.add(new SimpleConditionEvent(method, !entities.isEmpty(),
                        method.getFullName() + " returns " + entities));
            }
        };
    }

    private static ArchCondition<JavaClass> beRecords() {
        return new ArchCondition<>("be records") {
            @Override
            public void check(final JavaClass javaClass, final ConditionEvents events) {
                events.add(new SimpleConditionEvent(javaClass, javaClass.isRecord(),
                        javaClass.getName() + (javaClass.isRecord() ? " is" : " is not")
                                + " a record"));
            }
        };
    }

    /**
     * Money is {@code numeric(19,3)}; a VAT rate is a fraction with four decimals, so it is
     * {@code numeric(5,4)} and must not be mistaken for money. Both shapes are accepted, any
     * other is not — including a {@code BigDecimal} column with no precision at all, which
     * would let the database pick its own.
     */
    private static ArchCondition<JavaField> declarePrecisionNineteenAndScaleThree() {
        return new ArchCondition<>("declare precision 19 and scale 3, or 5 and 4 for a rate") {
            @Override
            public void check(final JavaField field, final ConditionEvents events) {
                if (!field.isAnnotatedWith(Column.class)) {
                    events.add(SimpleConditionEvent.violated(field,
                            field.getFullName() + " is a BigDecimal without a @Column declaration"));
                    return;
                }
                final Column column = field.getAnnotationOfType(Column.class);
                final boolean money = column.precision() == 19 && column.scale() == 3;
                final boolean rate = column.precision() == 5 && column.scale() == 4;
                events.add(new SimpleConditionEvent(field, money || rate,
                        field.getFullName() + " declares precision " + column.precision()
                                + " and scale " + column.scale()));
            }
        };
    }

    private static ArchCondition<JavaMethod> beAnnotatedAsReadOnlyTransactional() {
        return new ArchCondition<>("be annotated @Transactional(readOnly = true)") {
            @Override
            public void check(final JavaMethod method, final ConditionEvents events) {
                // Spring resolves @Transactional on the method first, then on the declaring
                // class. Reading only the method would flag SearchServiceImpl.search, which is
                // covered by a class-level annotation — a false positive, not a defect.
                final boolean readOnly = isReadOnlyTransactional(method)
                        || isReadOnlyTransactional(method.getOwner());
                events.add(new SimpleConditionEvent(method, readOnly,
                        method.getFullName() + (readOnly ? " is" : " is not")
                                + " annotated @Transactional(readOnly = true)"));
            }
        };
    }

    private static boolean isReadOnlyTransactional(
            final com.tngtech.archunit.core.domain.properties.CanBeAnnotated annotated) {
        if (!annotated.isAnnotatedWith(Transactional.class)) {
            return false;
        }
        if (annotated instanceof JavaMethod method) {
            return method.getAnnotationOfType(Transactional.class).readOnly();
        }
        return ((JavaClass) annotated).getAnnotationOfType(Transactional.class).readOnly();
    }

    private static ArchCondition<JavaClass> callFindAllWithoutArguments() {
        return new ArchCondition<>("call findAll() without arguments") {
            @Override
            public void check(final JavaClass javaClass, final ConditionEvents events) {
                for (final JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                    if ("findAll".equals(call.getTarget().getName())
                            && call.getTarget().getRawParameterTypes().isEmpty()) {
                        events.add(SimpleConditionEvent.violated(javaClass,
                                call.getOrigin().getFullName() + " calls "
                                        + call.getTarget().getFullName()));
                    }
                }
            }
        };
    }

    /**
     * Every raw type a declared type mentions, walking into generics: {@code ResponseEntity<Page<
     * Expense>>} yields ResponseEntity, Page and Expense. Without this an entity smuggled inside
     * a wrapper would pass unnoticed.
     */
    private static Set<JavaClass> erasuresOf(final JavaType type) {
        final Set<JavaClass> erasures = new LinkedHashSet<>();
        erasures.add(type.toErasure());
        if (type instanceof JavaParameterizedType parameterized) {
            for (final JavaType argument : parameterized.getActualTypeArguments()) {
                erasures.addAll(erasuresOf(argument));
            }
        }
        return erasures;
    }
}
