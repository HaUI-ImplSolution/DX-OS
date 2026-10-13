// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceAdministration;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceCreateCommand;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceCreated;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceMemberRole;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceMemberView;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceType;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceView;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WorkspaceService implements WorkspaceAdministration {

  private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);
  // No look-alike characters (0/O, 1/l/I) so a password read out over the phone is unambiguous.
  private static final String PASSWORD_ALPHABET =
      "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int PASSWORD_LENGTH = 14;

  private final WorkspaceRepository workspaceRepository;
  private final WorkspaceMemberRepository memberRepository;
  private final WorkspaceDirectoryService directory;
  private final WorkspaceTypes workspaceTypes;
  private final WorkspaceCodeGenerator codeGenerator;
  private final KeycloakAdminClient keycloakAdminClient;
  private final TransactionTemplate transactionTemplate;
  private final SecureRandom random = new SecureRandom();

  public WorkspaceService(WorkspaceRepository workspaceRepository,
      WorkspaceMemberRepository memberRepository, WorkspaceDirectoryService directory,
      WorkspaceTypes workspaceTypes, WorkspaceCodeGenerator codeGenerator,
      KeycloakAdminClient keycloakAdminClient, TransactionTemplate transactionTemplate) {
    this.workspaceRepository = workspaceRepository;
    this.memberRepository = memberRepository;
    this.directory = directory;
    this.workspaceTypes = workspaceTypes;
    this.codeGenerator = codeGenerator;
    this.keycloakAdminClient = keycloakAdminClient;
    this.transactionTemplate = transactionTemplate;
  }

  // The database work and the Keycloak call share one transaction, so a Keycloak failure rolls
  // the workspace back; if the commit itself fails after Keycloak succeeded, the new account is
  // deleted again.
  @Override
  public WorkspaceCreated create(WorkspaceCreateCommand command) {
    WorkspaceType type = workspaceTypes.require(command.type());
    boolean withAccount = command.username() != null && !command.username().isBlank();
    if (withAccount && (command.accountRole() == null || command.accountRole().isBlank())) {
      throw new IllegalArgumentException("accountRole is required when a username is given");
    }
    String temporaryPassword = withAccount ? generatePassword() : null;
    AtomicReference<String> createdUserId = new AtomicReference<>();
    try {
      Workspace saved = transactionTemplate.execute(status -> {
        Workspace workspace =
            workspaceRepository.saveAndFlush(Workspace.builder().code(codeGenerator.next(type))
                .type(type.type()).name(command.name().trim()).phone(command.phone())
                .address(command.address()).attributes(command.attributes()).build());
        if (withAccount) {
          String userId = keycloakAdminClient.createUser(command.username(), workspace.getName(),
              command.email(), temporaryPassword, command.accountRole());
          createdUserId.set(userId);
          memberRepository.saveAndFlush(WorkspaceMember.builder().workspaceId(workspace.getId())
              .userId(userId).role(WorkspaceMemberRole.OWNER).build());
        }
        return workspace;
      });
      return new WorkspaceCreated(WorkspaceDirectoryService.view(saved, createdUserId.get()),
          withAccount ? command.username() : null, temporaryPassword);
    } catch (RuntimeException e) {
      String userId = createdUserId.get();
      if (userId != null) {
        try {
          keycloakAdminClient.deleteUser(userId);
        } catch (RuntimeException cleanupFailure) {
          log.error("Could not remove Keycloak user {} after a failed workspace creation", userId);
        }
      }
      throw e;
    }
  }

  // The account becomes owner of a workspace that has none. An account owns at most one
  // workspace of a given type.
  @Override
  @Transactional
  public WorkspaceView linkOwner(String code, String keycloakUserId, String username) {
    Workspace workspace = requireWorkspace(code);
    if (memberRepository.findByWorkspaceIdAndRole(workspace.getId(), WorkspaceMemberRole.OWNER)
        .isPresent()) {
      throw new InvalidStateException("Workspace already has an account: " + code);
    }
    String userId = resolveAccount(keycloakUserId, username);
    for (WorkspaceMember owned : memberRepository.findByUserIdAndRole(userId,
        WorkspaceMemberRole.OWNER)) {
      workspaceRepository.findById(owned.getWorkspaceId())
          .filter(other -> other.getType().equals(workspace.getType())).ifPresent(other -> {
            throw new InvalidStateException(
                "Account is already linked to workspace " + other.getCode());
          });
    }
    memberRepository.findByWorkspaceIdAndUserId(workspace.getId(), userId)
        .ifPresentOrElse(existing -> {
          existing.setRole(WorkspaceMemberRole.OWNER);
          memberRepository.save(existing);
        }, () -> memberRepository.save(WorkspaceMember.builder().workspaceId(workspace.getId())
            .userId(userId).role(WorkspaceMemberRole.OWNER).build()));
    return directory.toView(workspace);
  }

  @Override
  @Transactional
  public WorkspaceMemberView addMember(String code, String keycloakUserId, String username,
      WorkspaceMemberRole role) {
    Workspace workspace = requireWorkspace(code);
    if (role == WorkspaceMemberRole.OWNER) {
      linkOwner(code, keycloakUserId, username);
      return memberRepository.findByWorkspaceIdAndRole(workspace.getId(), WorkspaceMemberRole.OWNER)
          .map(WorkspaceDirectoryService::toMemberView).orElseThrow();
    }
    String userId = resolveAccount(keycloakUserId, username);
    WorkspaceMember member =
        memberRepository.findByWorkspaceIdAndUserId(workspace.getId(), userId).orElseGet(
            () -> WorkspaceMember.builder().workspaceId(workspace.getId()).userId(userId).build());
    if (member.getRole() == WorkspaceMemberRole.OWNER) {
      throw new InvalidStateException("Account is the owner of workspace " + code);
    }
    member.setRole(WorkspaceMemberRole.MEMBER);
    return WorkspaceDirectoryService.toMemberView(memberRepository.save(member));
  }

  private Workspace requireWorkspace(String code) {
    return workspaceRepository.findByCode(code)
        .orElseThrow(() -> new ResourceNotFoundException("Workspace not found: " + code));
  }

  private String resolveAccount(String keycloakUserId, String username) {
    String userId = keycloakUserId != null && !keycloakUserId.isBlank() ? keycloakUserId
        : keycloakAdminClient.findUserId(username.strip()).orElseThrow(
            () -> new ResourceNotFoundException("Keycloak user not found: " + username));
    if (!keycloakAdminClient.userExists(userId)) {
      throw new ResourceNotFoundException("Keycloak user not found: " + userId);
    }
    return userId;
  }

  private String generatePassword() {
    StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
    for (int i = 0; i < PASSWORD_LENGTH; i++) {
      password.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
    }
    return password.toString();
  }
}
