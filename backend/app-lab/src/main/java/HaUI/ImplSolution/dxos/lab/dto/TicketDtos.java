// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.dto;

import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgePassage;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowEventResponse;
import HaUI.ImplSolution.dxos.lab.model.Ticket;
import HaUI.ImplSolution.dxos.lab.model.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

// Requests and responses of the tickets API.
public final class TicketDtos {

  public record TicketCreateRequest(@NotBlank @Size(max = 255) String title,
      @NotBlank @Size(max = 4000) String description, @NotBlank String categoryCode,
      TicketPriority priority, Long roomId, Long deviceId) {}

  public record TicketResponse(Long id, String code, String title, String description,
      String categoryCode, String categoryName, TicketPriority priority, String status,
      Long roomId, String roomCode, Long deviceId, String deviceAssetCode, String requesterId,
      String requesterName, String assigneeId, String assigneeName, Instant responseDueAt,
      Instant resolutionDueAt, Instant firstRespondedAt, Instant resolvedAt, Instant closedAt,
      String resolutionNote, String rootCause, int reopenCount, Integer rating,
      String ratingComment, boolean overdue, Instant createdAt, Instant updatedAt) {

    public static TicketResponse of(Ticket ticket) {
      var room = ticket.getRoom();
      var device = ticket.getDevice();
      return new TicketResponse(ticket.getId(), ticket.getCode(), ticket.getTitle(),
          ticket.getDescription(), ticket.getCategory().getCode(), ticket.getCategory().getName(),
          ticket.getPriority(), ticket.getStatus(), room == null ? null : room.getId(),
          room == null ? null : room.getCode(), device == null ? null : device.getId(),
          device == null ? null : device.getAssetCode(), ticket.getRequesterId(),
          ticket.getRequesterName(), ticket.getAssigneeId(), ticket.getAssigneeName(),
          ticket.getResponseDueAt(), ticket.getResolutionDueAt(), ticket.getFirstRespondedAt(),
          ticket.getResolvedAt(), ticket.getClosedAt(), ticket.getResolutionNote(),
          ticket.getRootCause(), ticket.getReopenCount(), ticket.getRating(),
          ticket.getRatingComment(), ticket.isOverdue(), ticket.getCreatedAt(),
          ticket.getUpdatedAt());
    }
  }

  public record TicketCommentRequest(@NotBlank @Size(max = 4000) String body, Boolean internal) {}

  public record TicketCommentResponse(Long id, String authorId, String authorName, boolean staff,
      boolean internal, String body, Instant createdAt) {}

  public record TicketAttachmentResponse(Long id, String filename, String contentType,
      long sizeBytes, String url, Instant createdAt) {}

  public record TicketDetailResponse(TicketResponse ticket, List<TicketCommentResponse> comments,
      List<TicketAttachmentResponse> attachments, List<WorkflowEventResponse> timeline) {}

  public record AssignRequest(@NotBlank String assigneeId, @NotBlank String assigneeName) {}

  public record WaitRequest(@Size(max = 4000) String message) {}

  public record ResolveRequest(@Size(max = 2000) String resolutionNote,
      @Size(max = 1000) String rootCause) {}

  public record ConfirmRequest(Integer rating, @Size(max = 1000) String comment) {}

  public record ReopenRequest(@Size(max = 1000) String reason) {}

  public record RejectRequest(@Size(max = 1000) String reason) {}

  public record TechnicianLoadResponse(String userId, String name, long openTickets) {}

  public record TicketSuggestRequest(@Size(max = 255) String title,
      @NotBlank @Size(max = 4000) String description) {}

  // matchedKeywords and reasons explain the suggestion; relatedKnowledge holds quoted passages.
  public record TicketSuggestionResponse(String categoryCode, String categoryName,
      TicketPriority priority, List<String> matchedKeywords, List<String> reasons,
      boolean knowledgeAvailable, List<RelatedPassage> relatedKnowledge) {}

  public record RelatedPassage(String content, String documentTitle, double score,
      List<KnowledgeDtos.Source> sources) {

    public static RelatedPassage of(KnowledgePassage passage) {
      return new RelatedPassage(passage.content(), passage.documentTitle(), passage.score(),
          passage.sources().stream().map(KnowledgeDtos.Source::of).toList());
    }
  }

  private TicketDtos() {}
}
