package np.com.thapanarayan.backend.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the modular-monolith boundary at build time: a module's {@code internal}
 * package is private to that module. Any cross-module reference into another
 * module's {@code internal} fails the build. Modules collaborate only through
 * their published {@code api} packages.
 */
class ModuleBoundaryTest {

    private static final String BASE = "np.com.thapanarayan.backend";

    private static final List<String> MODULES = List.of(
            "reference", "ingestion", "marketdata", "indicator", "signal",
            "backtest", "charting", "watchlist", "notification", "iam", "platform");

    @Test
    void noModuleAccessesAnotherModulesInternalPackage() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE);

        for (String module : MODULES) {
            ArchRule rule = noClasses()
                    .that().resideOutsideOfPackage(".." + module + "..")
                    .should().dependOnClassesThat().resideInAPackage(".." + module + ".internal..")
                    .as("classes outside module '" + module
                            + "' must not depend on its internal package")
                    .allowEmptyShould(true);
            rule.check(classes);
        }
    }
}
