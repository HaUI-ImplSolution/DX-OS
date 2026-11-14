// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// Layering rules of the packages of all modules, which sit together on the classpath of
// dx-engine.
class ArchitectureTest {

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("HaUI.ImplSolution.dxos");
  }

  @Test
  void controllersGoThroughServicesNotRepositories() {
    noClasses()
        .that()
        .haveSimpleNameEndingWith("Controller")
        .should()
        .dependOnClassesThat()
        .haveSimpleNameEndingWith("Repository")
        .check(classes);
  }

  @Test
  void servicesDoNotDependOnControllers() {
    noClasses()
        .that()
        .haveSimpleNameEndingWith("Service")
        .should()
        .dependOnClassesThat()
        .haveSimpleNameEndingWith("Controller")
        .check(classes);
  }

  @Test
  void entitiesDoNotDependOnTheLayersAboveThem() {
    noClasses()
        .that()
        .resideInAPackage("..model..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..service..", "..controller..", "..repository..")
        .check(classes);
  }

  @Test
  void dtosDoNotDependOnTheLayersAboveThem() {
    noClasses()
        .that()
        .resideInAPackage("..dto..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..service..", "..controller..", "..repository..")
        .check(classes);
  }

  @Test
  void modulesHaveNoCycles() {
    slices().matching("HaUI.ImplSolution.dxos.(*)..").should().beFreeOfCycles().check(classes);
  }
}
