// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab;

import HaUI.ImplSolution.dxos.core.api.security.RoleDefinition;
import HaUI.ImplSolution.dxos.core.api.security.RoleProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// The roles of the Lab app. Technicians and administrators work on requests of other people.
@Configuration
public class LabSecurityPolicies {

  public static final String STUDENT = "STUDENT";
  public static final String LECTURER = "LECTURER";
  public static final String TECHNICIAN = "TECHNICIAN";
  public static final String ADMIN = "ADMIN";

  @Bean
  public RoleProvider labRoleProvider() {
    return () -> List.of(
        new RoleDefinition(STUDENT, "Student who reports incidents and borrows devices", false),
        new RoleDefinition(LECTURER, "Lecturer who books rooms and approves student loans", false),
        new RoleDefinition(TECHNICIAN, "IT technician who handles tickets and equipment", true),
        new RoleDefinition(ADMIN, "Lab administrator", true));
  }
}
