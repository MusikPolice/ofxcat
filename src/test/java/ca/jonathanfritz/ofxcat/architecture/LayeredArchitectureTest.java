package ca.jonathanfritz.ofxcat.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "ca.jonathanfritz.ofxcat", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

    // -- Layered architecture definition --
    //
    // OfxCat (entry point) — orchestrates everything
    //     |
    // service / cli — business logic and user interaction
    //     |           cli uses DTOs and IO types for display/prompts
    //     |
    // matching — token matching, keyword rules (uses DAOs for lookups)
    // cleaner  — bank-specific transaction cleaning (bridges io -> dto)
    //     |
    // datastore — DAOs + DTOs + database utilities
    // io        — OFX parsing (self-contained)
    //     |
    // config / utils / exception — leaf packages, no upward dependencies

    @ArchTest
    static final ArchRule layeredArchitecture = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("EntryPoint")
            .definedBy("ca.jonathanfritz.ofxcat")
            .layer("Service")
            .definedBy("ca.jonathanfritz.ofxcat.service..")
            .layer("CLI")
            .definedBy("ca.jonathanfritz.ofxcat.cli..")
            .layer("Matching")
            .definedBy("ca.jonathanfritz.ofxcat.matching..")
            .layer("Cleaner")
            .definedBy("ca.jonathanfritz.ofxcat.cleaner..")
            .layer("Datastore")
            .definedBy("ca.jonathanfritz.ofxcat.datastore..")
            .layer("IO")
            .definedBy("ca.jonathanfritz.ofxcat.io..")
            .layer("Config")
            .definedBy("ca.jonathanfritz.ofxcat.config..")
            .layer("Utils")
            .definedBy("ca.jonathanfritz.ofxcat.utils..")
            .layer("Exception")
            .definedBy("ca.jonathanfritz.ofxcat.exception..")
            .whereLayer("EntryPoint")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Service")
            .mayOnlyBeAccessedByLayers("EntryPoint")
            .whereLayer("CLI")
            .mayOnlyBeAccessedByLayers("EntryPoint", "Service")
            .whereLayer("Matching")
            .mayOnlyBeAccessedByLayers("EntryPoint", "Service")
            .whereLayer("Cleaner")
            .mayOnlyBeAccessedByLayers("EntryPoint", "Service")
            .whereLayer("IO")
            .mayOnlyBeAccessedByLayers("EntryPoint", "Service", "Cleaner", "CLI")
            .whereLayer("Datastore")
            .mayOnlyBeAccessedByLayers("EntryPoint", "Service", "Matching", "Cleaner", "CLI")
            .whereLayer("Config")
            .mayOnlyBeAccessedByLayers("EntryPoint", "Service", "Matching")
            .whereLayer("Utils")
            .mayOnlyBeAccessedByLayers("EntryPoint", "Service", "Matching", "Cleaner", "CLI", "IO", "Datastore")
            .whereLayer("Exception")
            .mayOnlyBeAccessedByLayers("EntryPoint", "Service", "Matching", "Cleaner", "CLI", "IO", "Datastore");

    // -- Leaf package isolation --

    @ArchTest
    static final ArchRule dtosShouldNotDependOnOtherLayers = noClasses()
            .that()
            .resideInAPackage("ca.jonathanfritz.ofxcat.datastore.dto..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "ca.jonathanfritz.ofxcat.service..",
                    "ca.jonathanfritz.ofxcat.cli..",
                    "ca.jonathanfritz.ofxcat.matching..",
                    "ca.jonathanfritz.ofxcat.cleaner..",
                    "ca.jonathanfritz.ofxcat.io..",
                    "ca.jonathanfritz.ofxcat.config..")
            .as("DTOs should be pure data classes with no dependencies on other layers");

    @ArchTest
    static final ArchRule ioShouldBeIsolated = noClasses()
            .that()
            .resideInAPackage("ca.jonathanfritz.ofxcat.io..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "ca.jonathanfritz.ofxcat.service..",
                    "ca.jonathanfritz.ofxcat.cli..",
                    "ca.jonathanfritz.ofxcat.matching..",
                    "ca.jonathanfritz.ofxcat.cleaner..",
                    "ca.jonathanfritz.ofxcat.datastore..")
            .as("IO classes should only parse OFX files, not depend on other layers");

    // -- DAO layer protection --

    @ArchTest
    static final ArchRule daosShouldNotDependOnServices = noClasses()
            .that()
            .resideInAPackage("ca.jonathanfritz.ofxcat.datastore..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "ca.jonathanfritz.ofxcat.service..",
                    "ca.jonathanfritz.ofxcat.cli..",
                    "ca.jonathanfritz.ofxcat.matching..",
                    "ca.jonathanfritz.ofxcat.cleaner..",
                    "ca.jonathanfritz.ofxcat.io..")
            .as("DAOs should only access the database, not depend on services or higher layers");

    // -- No circular dependencies between top-level packages --

    @ArchTest
    static final ArchRule noCyclicDependencies = slices().matching("ca.jonathanfritz.ofxcat.(*)..")
            .should()
            .beFreeOfCycles()
            .as("No circular dependencies between packages");

    // -- Safety net: every class must belong to a known package --
    // If a new package is introduced, this test forces you to add it to the layer definitions above.

    @ArchTest
    static final ArchRule allClassesBelongToAKnownPackage = classes()
            .that()
            .resideInAPackage("ca.jonathanfritz.ofxcat..")
            .should()
            .resideInAnyPackage(
                    "ca.jonathanfritz.ofxcat",
                    "ca.jonathanfritz.ofxcat.cli..",
                    "ca.jonathanfritz.ofxcat.cleaner..",
                    "ca.jonathanfritz.ofxcat.config..",
                    "ca.jonathanfritz.ofxcat.datastore..",
                    "ca.jonathanfritz.ofxcat.exception..",
                    "ca.jonathanfritz.ofxcat.io..",
                    "ca.jonathanfritz.ofxcat.matching..",
                    "ca.jonathanfritz.ofxcat.service..",
                    "ca.jonathanfritz.ofxcat.utils..")
            .as("All classes must belong to a known package — add new packages to the layer definitions above");
}
