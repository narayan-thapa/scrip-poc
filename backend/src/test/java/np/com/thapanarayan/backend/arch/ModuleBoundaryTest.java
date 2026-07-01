package np.com.thapanarayan.backend.arch;

import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Enforces the modular-monolith boundaries at build time (plain JUnit, no Spring context, no Docker).
 *
 * <p>The one rule that defines the architecture: a class in one module may never reach into another
 * module's {@code internal} package — cross-module collaboration goes through the published
 * {@code api} packages only. A violation fails the build.
 */
@AnalyzeClasses(
        packages = ModuleBoundaryTest.BASE,
        importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    static final String BASE = "np.com.thapanarayan.backend";

    @ArchTest
    static final ArchRule modules_must_not_depend_on_other_modules_internals =
            noClasses()
                    .that(are(new com.tngtech.archunit.base.DescribedPredicate<JavaClass>("in a feature module") {
                        @Override
                        public boolean test(JavaClass clazz) {
                            return moduleOf(clazz.getPackageName()) != null;
                        }
                    }))
                    .should(reachIntoAnotherModulesInternals())
                    .as("no module may depend on another module's internal package")
                    .because("cross-module access must go through published api packages");

    private static ArchCondition<JavaClass> reachIntoAnotherModulesInternals() {
        return new ArchCondition<>("depend on another module's internal package") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                String fromModule = moduleOf(clazz.getPackageName());
                for (Dependency dep : clazz.getDirectDependenciesFromSelf()) {
                    String targetPkg = dep.getTargetClass().getPackageName();
                    String toModule = moduleOf(targetPkg);
                    boolean crossModuleInternal = toModule != null
                            && !toModule.equals(fromModule)
                            && (targetPkg.equals(BASE + "." + toModule + ".internal")
                                    || targetPkg.startsWith(BASE + "." + toModule + ".internal."));
                    if (crossModuleInternal) {
                        events.add(SimpleConditionEvent.violated(clazz, dep.getDescription()));
                    }
                }
            }
        };
    }

    /** The module name is the package segment directly under {@link #BASE}, or null if not in a module. */
    static String moduleOf(String pkg) {
        String prefix = BASE + ".";
        if (!pkg.startsWith(prefix)) {
            return null;
        }
        String rest = pkg.substring(prefix.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
