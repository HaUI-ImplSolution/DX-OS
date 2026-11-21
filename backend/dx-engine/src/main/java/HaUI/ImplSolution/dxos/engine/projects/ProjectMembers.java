// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.projects;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Who belongs to each project and at which level. A member is a person in the scope of the project;
// an administrator of the project also manages its members and the roles of its models. The user
// id is the account id of the identity provider, with no link to it, so a row of an account that no
// longer exists is only left out when read.
@Component
public class ProjectMembers {

  public static final String MEMBER = "MEMBER";
  public static final String ADMIN = "ADMIN";

  public record Member(String userId, String level) {}

  private final JdbcTemplate jdbc;

  public ProjectMembers(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public static boolean isLevel(String level) {
    return MEMBER.equals(level) || ADMIN.equals(level);
  }

  // The members of one project, in the order they joined.
  public List<Member> of(String project) {
    return jdbc.query(
        "SELECT user_id, level FROM engine.project_member WHERE project = ? ORDER BY added_at,"
            + " user_id",
        (rows, n) -> new Member(rows.getString("user_id"), rows.getString("level")),
        project);
  }

  // Project name to the level this person has in it.
  public Map<String, String> levelsOf(String userId) {
    Map<String, String> levels = new LinkedHashMap<>();
    jdbc.query(
        "SELECT project, level FROM engine.project_member WHERE user_id = ? ORDER BY project",
        rows -> {
          levels.put(rows.getString("project"), rows.getString("level"));
        },
        userId);
    return levels;
  }

  public Optional<String> levelOf(String project, String userId) {
    return jdbc
        .queryForList(
            "SELECT level FROM engine.project_member WHERE project = ? AND user_id = ?",
            String.class,
            project,
            userId)
        .stream()
        .findFirst();
  }

  // The projects this person administers.
  public Set<String> administered(String userId) {
    return new TreeSet<>(
        jdbc.queryForList(
            "SELECT project FROM engine.project_member WHERE user_id = ? AND level = 'ADMIN'",
            String.class,
            userId));
  }

  // Whether this person is a member, of any level, of one of the projects.
  public boolean isMemberOfAny(Collection<String> projects, String userId) {
    return projects.stream().anyMatch(project -> levelOf(project, userId).isPresent());
  }

  // The accounts that belong to at least one of the projects.
  public Set<String> userIdsOf(Collection<String> projects) {
    Set<String> ids = new TreeSet<>();
    projects.forEach(project -> of(project).forEach(member -> ids.add(member.userId())));
    return ids;
  }

  // Project name to the number of rows it has.
  public Map<String, Integer> counts() {
    Map<String, Integer> counts = new LinkedHashMap<>();
    jdbc.query(
        "SELECT project, count(*) AS n FROM engine.project_member GROUP BY project",
        rows -> {
          counts.put(rows.getString("project"), rows.getInt("n"));
        });
    return counts;
  }

  // Adds the person or changes the level.
  public void set(String project, String userId, String level) {
    jdbc.update(
        "INSERT INTO engine.project_member (project, user_id, level) VALUES (?, ?, ?) "
            + "ON CONFLICT (project, user_id) DO UPDATE SET level = EXCLUDED.level",
        project,
        userId,
        level);
  }

  public void remove(String project, String userId) {
    jdbc.update(
        "DELETE FROM engine.project_member WHERE project = ? AND user_id = ?", project, userId);
  }
}
