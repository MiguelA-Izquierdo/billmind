package dev.izquierdo.billmind.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The layering rules of {@code docs/ARCHITECTURE.md}, executable.
 *
 * <p>Domain purity and the dependency direction are invariants of this codebase, not conventions
 * agreed in a review: a violation fails {@code ./mvnw test} rather than waiting for someone to
 * notice it. Cross-context imports are deliberately <em>not</em> forbidden — BillMind is a modular
 * monolith and that coupling is a decision (see ARCHITECTURE.md); the rules below constrain the
 * layers, which is where an accidental import actually costs something.
 */
class ArchitectureRulesTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("dev.izquierdo.billmind");

    @Test
    void shouldKeepDomainFreeOfFrameworkDependencies() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.validation..",
                        "dev.langchain4j..",
                        "lombok..",
                        "com.fasterxml.jackson..",
                        "io.micrometer..",
                        "org.slf4j..")
                .because("the domain layer models the business and must stay framework-agnostic; "
                        + "only java.* and other domain types may reach it");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void shouldKeepDomainIndependentOfOuterLayers() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..")
                .because("dependencies point inwards: infrastructure -> application -> domain");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void shouldKeepApplicationIndependentOfInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("use cases talk to the outside world through domain ports, never through "
                        + "the adapters that implement them");

        rule.check(PRODUCTION_CLASSES);
    }
}