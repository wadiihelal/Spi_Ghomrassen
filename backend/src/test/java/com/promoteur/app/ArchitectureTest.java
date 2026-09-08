package com.promoteur.app;

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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
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
 * test says anything. Eighty lines of ArchUnit make them permanent.</p>
 *
 * <p><b>On {@code @DisplayName}</b>: unlike everywhere else in this suite, the rules below carry
 * no {@code @DisplayName}. An {@code @ArchTest} is a {@code static final ArchRule} field, not a
 * method, so the annotation does not apply; the sentence goes to {@code as(...)} on the rule
 * itself, which is what appears in the report.</p>
 */
@AnalyzeClasses(packages = "com.promoteur.app", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // ---------------------------------------------------------------- 4.1 layers

    @ArchTest
    static final ArchRule layersAreRespected = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Controller").definedBy("..controller..")
            .layer("Service").definedBy("..service..")
            .layer("Repository").definedBy("..repository..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
            // consideringOnlyDependenciesInLayers matters here: the start-up seeds live in
            // config/, which is not a layer, and inject repositories directly. That is a
            // legitimate case, not a breach of the layering.
            .as("the layers are respected: nothing depends on a controller, "
                    + "and only services reach repositories");

    @ArchTest
    static final ArchRule noControllerDependsOnARepository = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .as("no controller depends on a repository");

    @ArchTest
    static final ArchRule noControllerDependsOnAServiceImplementation = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..service.impl..")
            .as("no controller depends on a service implementation, only on its interface");

    @ArchTest
    static final ArchRule everyServiceImplementationImplementsAnInterface = classes()
            .that().resideInAPackage("..service.impl..")
            .and().areNotInterfaces()
            .and(areNotPackagePrivateHelpers())
            .should(implementAnInterfaceFromServicePackage())
            .as("every class in service.impl implements an interface from service");

    // ------------------------------------------------------------ 4.2 DTO boundary

    @ArchTest
    static final ArchRule noControllerMethodReturnsAnEntity = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
            .and().arePublic()
            .should(returnAJpaEntity())
            .as("no controller method returns a JPA entity, "
                    + "directly or wrapped in ResponseEntity, Page or List");

    @ArchTest
    static final ArchRule noDtoExposesAnEntity = noFields()
            .that().areDeclaredInClassesThat().resideInAPackage("..dto..")
            .should().haveRawType(resideInAPackage("..entity.."))
            .as("no DTO exposes a JPA entity in a field or a record component");

    @ArchTest
    static final ArchRule everyResponseDtoIsARecord = classes()
            .that().resideInAPackage("..dto.response..")
            .should(beRecords())
            .as("every response DTO is a record");

    // -------------------------------------------------------------- 4.3 the money rule

    @ArchTest
    static final ArchRule noFieldIsADoubleOrAFloat = noFields()
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
            .that().areDeclaredInClassesThat().resideInAPackage("..entity..")
            .and().haveRawType(BigDecimal.class)
            .should(declarePrecisionNineteenAndScaleThree())
            .as("every BigDecimal column declares precision 19 and scale 3, "
                    + "or the scale a rate needs");

    // ------------------------------------------------- 4.4 transactions and reads

    @ArchTest
    static final ArchRule everyFinderIsReadOnlyTransactional = methods()
            .that().areDeclaredInClassesThat().resideInAPackage("..service.impl..")
            .and().arePublic()
            .and(haveAFinderName())
            .should(beAnnotatedAsReadOnlyTransactional())
            .as("every read-only finder is annotated transactional read-only");

    @ArchTest
    static final ArchRule noServiceCallsFindAllWithoutArguments = noClasses()
            .that().resideInAPackage("..service..")
            .should(callFindAllWithoutArguments())
            .as("no service calls findAll() without arguments: "
                    + "a list is filtered and paged in SQL, never regrouped in Java");

    // ------------------------------------------------------------- 4.5 hygiene

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

    @ArchTest
    static final ArchRule noCyclesBetweenPackages = slices()
            .matching("com.promoteur.app.(*)..")
            .should().beFreeOfCycles()
            .as("no cycles between packages");

    // ---------------------------------------------------------------- conditions

    /**
     * {@code PdfLetterhead} is a package-private helper of {@code service.impl}, not a service:
     * it holds the shared look of the printed documents (UX-06) and is deliberately not exposed
     * behind an interface.
     */
    private static com.tngtech.archunit.base.DescribedPredicate<JavaClass> areNotPackagePrivateHelpers() {
        return new com.tngtech.archunit.base.DescribedPredicate<>("are not package-private helpers") {
            @Override
            public boolean test(final JavaClass javaClass) {
                return javaClass.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC);
            }
        };
    }

    private static ArchCondition<JavaClass> implementAnInterfaceFromServicePackage() {
        return new ArchCondition<>("implement an interface from the service package") {
            @Override
            public void check(final JavaClass javaClass, final ConditionEvents events) {
                final boolean implemented = javaClass.getRawInterfaces().stream()
                        .anyMatch(candidate -> candidate.getPackageName()
                                .equals("com.promoteur.app.service"));
                events.add(new SimpleConditionEvent(javaClass, implemented,
                        javaClass.getName() + (implemented ? " implements " : " implements no ")
                                + "an interface from com.promoteur.app.service"));
            }
        };
    }

    private static ArchCondition<JavaMethod> returnAJpaEntity() {
        return new ArchCondition<>("return a JPA entity") {
            @Override
            public void check(final JavaMethod method, final ConditionEvents events) {
                final Set<String> entities = erasuresOf(method.getReturnType()).stream()
                        .filter(type -> type.getPackageName().startsWith("com.promoteur.app.entity"))
                        .map(JavaClass::getSimpleName)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
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

    private static com.tngtech.archunit.base.DescribedPredicate<JavaMethod> haveAFinderName() {
        return new com.tngtech.archunit.base.DescribedPredicate<>("have a finder name") {
            @Override
            public boolean test(final JavaMethod method) {
                final String name = method.getName();
                return name.startsWith("find") || name.startsWith("get") || name.startsWith("list")
                        || name.startsWith("search");
            }
        };
    }

    private static ArchCondition<JavaMethod> beAnnotatedAsReadOnlyTransactional() {
        return new ArchCondition<>("be annotated @Transactional(readOnly = true)") {
            @Override
            public void check(final JavaMethod method, final ConditionEvents events) {
                final boolean readOnly = method.isAnnotatedWith(Transactional.class)
                        && method.getAnnotationOfType(Transactional.class).readOnly();
                events.add(new SimpleConditionEvent(method, readOnly,
                        method.getFullName() + (readOnly ? " is" : " is not")
                                + " annotated @Transactional(readOnly = true)"));
            }
        };
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
