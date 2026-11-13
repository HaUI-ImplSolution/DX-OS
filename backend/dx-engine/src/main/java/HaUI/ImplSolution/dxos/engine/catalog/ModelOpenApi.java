// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.catalog;

import HaUI.ImplSolution.dxos.engine.meta.MetaController.EntityMeta;
import HaUI.ImplSolution.dxos.engine.meta.MetaController.FieldMeta;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Action;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

// The OpenAPI document of one applied model, read from its model and metadata; it names no
// entity or role itself.
@Component
public class ModelOpenApi {

  // The codes the core answers with, as they appear in errorCode.
  static final List<String> ERROR_CODES =
      List.of(
          "ERR_BAD_REQUEST",
          "ERR_VALIDATION",
          "ERR_UNAUTHENTICATED",
          "ERR_FORBIDDEN",
          "ERR_ACTION_NOT_ALLOWED",
          "ERR_NOT_FOUND",
          "ERR_INVALID_STATE",
          "ERR_CONCURRENT_MODIFICATION",
          "ERR_METHOD_NOT_ALLOWED",
          "ERR_UNSUPPORTED_MEDIA_TYPE",
          "ERR_FILE_INFECTED",
          "ERR_SCAN_UNAVAILABLE",
          "ERR_TOO_MANY_REQUESTS",
          "ERR_INTERNAL");

  private static final String JSON = "application/json";
  private static final String SCHEMAS = "#/components/schemas/";

  public OpenAPI build(Model model, List<EntityMeta> entities) {
    Components components = new Components();
    Paths paths = new Paths();
    addShared(components);
    for (EntityMeta entity : entities) {
      addEntity(model, entity, components, paths);
    }
    components.addSecuritySchemes(
        "bearerAuth",
        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
    return new OpenAPI()
        .info(
            new Info()
                .title("DX-OS model " + model.name())
                .version("v1")
                .description(
                    "The data API of the applied model "
                        + model.name()
                        + ", written out from the model file as it runs now."
                        + " Sign in with Keycloak and send the access token as a bearer token."))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
        .paths(paths)
        .components(components);
  }

  // The same text on the wire as the document, so a caller gets exactly what a tool reads.
  public String toJson(OpenAPI document) {
    try {
      return Json.mapper().writeValueAsString(document);
    } catch (Exception e) {
      throw new IllegalStateException("The OpenAPI document cannot be written", e);
    }
  }

  private void addShared(Components components) {
    Schema<?> error =
        new ObjectSchema()
            .addProperty("success", new BooleanSchema()._default(false))
            .addProperty("message", new StringSchema())
            .addProperty("errorCode", new StringSchema()._enum(ERROR_CODES))
            .addProperty(
                "data",
                new ObjectSchema()
                    .nullable(true)
                    .description(
                        "Detail of the error: for a 400 the problem of each field by name,"
                            + " for a 403 or 409 data.reason, one of CANNOT_CREATE, KEPT_BY_PROCESS,"
                            + " NO_ROLE_FOR_ENTITY, NOT_ASSIGNER, PARENT_OUT_OF_REACH, READ_ONLY,"
                            + " SLOT_TAKEN, STATUS_BY_PROCESS_ONLY, STILL_REFERENCED, UNIT_OUT_OF_REACH,"
                            + " WRONG_STATE, RECORD_CLOSED, OWNER_ONLY, HANDLER_ONLY, SELF_DECISION,"
                            + " ASSIGNED_TO_OTHER, CONDITION_NOT_MET or IDEMPOTENCY_KEY_REUSED"))
            .addProperty("timestamp", new DateTimeSchema());
    components.addSchemas("ApiError", error);
    components.addSchemas(
        "ActionNote",
        new ObjectSchema()
            .addProperty(
                "note",
                new StringSchema()
                    .description("Required by the actions that say so, and kept in the history")));
    components.addSchemas(
        "AssignBody",
        new ObjectSchema()
            .addProperty(
                "assignee",
                new StringSchema()
                    .nullable(true)
                    .description("User id; null releases the record")));
    components.addSchemas("ResponseVoid", envelope(new ObjectSchema().nullable(true)));
    components.addSchemas("ResponseObject", envelope(new ObjectSchema()));
    components.addSchemas("ResponseList", envelope(new ArraySchema().items(new ObjectSchema())));
  }

  private void addEntity(Model model, EntityMeta entity, Components components, Paths paths) {
    String name = entity.name();
    String type = pascal(name);
    boolean process = entity.hasProcess();
    components.addSchemas(type, record(model, entity));
    components.addSchemas(type + "Input", input(model, entity));
    components.addSchemas("Response" + type, envelope(ref(type)));
    components.addSchemas(
        "Page" + type,
        new ObjectSchema()
            .addProperty("items", new ArraySchema().items(ref(type)))
            .addProperty("page", new IntegerSchema())
            .addProperty("size", new IntegerSchema())
            .addProperty("totalElements", new IntegerSchema().format("int64"))
            .addProperty("totalPages", new IntegerSchema()));
    components.addSchemas("ResponsePage" + type, envelope(ref("Page" + type)));

    String base = "/api/v1/data/" + model.name() + "/" + name;
    String one = base + "/{id}";

    PathItem collection =
        new PathItem()
            .get(
                operation(
                        "list" + type,
                        "List the records of " + label(entity),
                        "A role sees only the rows its access reaches. filter.<field>=<value>, or"
                            + " filter.<field>.<op> with op eq, ne, lt, lte, gt, gte, like or in.")
                    .parameters(listParameters(entity))
                    .responses(
                        responses(
                            "ResponsePage" + type,
                            "200",
                            "A page of records",
                            "400",
                            "401",
                            "403")))
            .post(
                operation(
                        "create" + type,
                        "Create a " + label(entity) + " record",
                        process ? "The status cannot be sent; the process sets it." : null)
                    .addParametersItem(idempotencyKey())
                    .requestBody(body(type + "Input"))
                    .responses(
                        replayable(
                            responses(
                                "Response" + type,
                                "200",
                                "The record as stored",
                                "400",
                                "401",
                                "403",
                                "409"))));
    paths.addPathItem(base, collection);

    PathItem record =
        new PathItem()
            .get(
                operation("get" + type, "Get one " + label(entity) + " record", null)
                    .addParametersItem(idParameter())
                    .responses(
                        responses("Response" + type, "200", "The record", "401", "403", "404")))
            .patch(
                operation(
                        "update" + type,
                        "Change the given fields of a record",
                        "PUT does the same. The body may carry version as read with the record; the change"
                            + " then applies only to that version, and 409 ERR_CONCURRENT_MODIFICATION"
                            + " answers when the record changed in between.")
                    .addParametersItem(idParameter())
                    .requestBody(body(type + "Input"))
                    .responses(
                        responses(
                            "Response" + type,
                            "200",
                            "The record as stored",
                            "400",
                            "401",
                            "403",
                            "404",
                            "409")));
    if (!process) {
      record.delete(
          operation("delete" + type, "Delete a record", null)
              .addParametersItem(idParameter())
              .responses(responses("ResponseVoid", "200", "Deleted", "401", "403", "404", "409")));
    }
    paths.addPathItem(one, record);

    if (process) {
      addProcess(model, entity, type, base, one, paths);
    }
    if (entity.fields().stream().anyMatch(f -> "file".equals(f.type()))) {
      addFiles(entity, type, one, paths);
    }
  }

  private void addProcess(
      Model model, EntityMeta entity, String type, String base, String one, Paths paths) {
    paths.addPathItem(
        base + "/stats",
        new PathItem()
            .get(
                operation(
                        "stats" + type,
                        "Count the records the caller can see",
                        "Takes the filter parameters of the list. The answer has total, byStatus, overdue and the"
                            + " time figures of the entity.")
                    .parameters(filterParameters(entity))
                    .responses(responses("ResponseObject", "200", "The counts", "401", "403"))));
    for (Action action : entity.process().actions()) {
      String words = (action.by() == null ? "" : action.by().name().toLowerCase(Locale.ROOT));
      StringBuilder text =
          new StringBuilder("Taken by the ")
              .append(words)
              .append(" from the state ")
              .append(String.join(", ", action.from()))
              .append(action.to() == null ? "" : " to " + action.to())
              .append('.');
      if (action.needsNote()) {
        text.append(" A note is required.");
      }
      if (action.require() != null && !action.require().isEmpty()) {
        text.append(" These fields must hold a value first: ")
            .append(String.join(", ", action.require()))
            .append('.');
      }
      if (action.when() != null) {
        text.append(" Only when ")
            .append(action.when().field())
            .append(' ')
            .append(action.when().op())
            .append(' ')
            .append(action.when().value())
            .append('.');
      }
      if (action.check() != null) {
        text.append(
            " A service checks the action before the record moves and can refuse it (403,"
                + " reason CHECK_DENIED) or be unavailable (503, reason CHECK_UNAVAILABLE).");
      }
      Operation operation =
          operation(
                  "action" + type + pascal(action.name()),
                  (action.label() == null ? action.name() : action.label())
                      + " ("
                      + action.name()
                      + ")",
                  text.toString())
              .addParametersItem(idParameter())
              .addParametersItem(idempotencyKey())
              .requestBody(body("ActionNote", false))
              .responses(
                  replayable(
                      responses(
                          "Response" + type,
                          "200",
                          "The record after the action",
                          "400",
                          "401",
                          "403",
                          "404",
                          "409")));
      paths.addPathItem(one + "/actions/" + action.name(), new PathItem().post(operation));
    }
    paths.addPathItem(
        one + "/assign",
        new PathItem()
            .post(
                operation(
                        "assign" + type,
                        "Assign a record to a user who can handle it",
                        "null releases the record.")
                    .addParametersItem(idParameter())
                    .requestBody(body("AssignBody"))
                    .responses(
                        responses(
                            "Response" + type, "200", "The record", "400", "401", "403", "404"))));
    paths.addPathItem(
        one + "/handlers",
        new PathItem()
            .get(
                operation("handlers" + type, "The users a record can be assigned to", null)
                    .addParametersItem(idParameter())
                    .responses(
                        responses(
                            "ResponseList",
                            "200",
                            "Users with id and name",
                            "401",
                            "403",
                            "404"))));
    paths.addPathItem(
        one + "/events",
        new PathItem()
            .get(
                operation("events" + type, "The history of a record, oldest first", null)
                    .addParametersItem(idParameter())
                    .responses(
                        responses(
                            "ResponseList", "200", "Lines of the history", "401", "403", "404"))));
  }

  private void addFiles(EntityMeta entity, String type, String one, Paths paths) {
    List<String> fileFields =
        entity.fields().stream().filter(f -> "file".equals(f.type())).map(FieldMeta::name).toList();
    Parameter field =
        new QueryParameter()
            .name("field")
            .required(true)
            .schema(new StringSchema()._enum(fileFields));
    PathItem files =
        new PathItem()
            .get(
                operation("listFiles" + type, "The files attached to a record", null)
                    .addParametersItem(idParameter())
                    .responses(
                        responses("ResponseList", "200", "The attachments", "401", "403", "404")))
            .post(
                operation(
                        "uploadFile" + type,
                        "Attach a file to a record",
                        "Multipart form data with the part file; the types accepted are set by the"
                            + " deployment, and an infected file is refused.")
                    .addParametersItem(idParameter())
                    .addParametersItem(field)
                    .requestBody(
                        new RequestBody()
                            .required(true)
                            .content(
                                new Content()
                                    .addMediaType(
                                        "multipart/form-data",
                                        new MediaType()
                                            .schema(
                                                new ObjectSchema()
                                                    .addProperty(
                                                        "file",
                                                        new StringSchema().format("binary"))))))
                    .responses(
                        responses(
                            "ResponseObject",
                            "200",
                            "The attachment",
                            "400",
                            "401",
                            "403",
                            "404",
                            "422",
                            "503")));
    paths.addPathItem(one + "/files", files);
    Parameter fileId =
        new PathParameter().name("fileId").schema(new IntegerSchema().format("int64"));
    paths.addPathItem(
        one + "/files/{fileId}/link",
        new PathItem()
            .get(
                operation(
                        "fileLink" + type,
                        "A link to download a file, valid for a short time",
                        null)
                    .addParametersItem(idParameter())
                    .addParametersItem(fileId)
                    .responses(
                        responses("ResponseObject", "200", "The link", "401", "403", "404"))));
    paths.addPathItem(
        one + "/files/{fileId}",
        new PathItem()
            .delete(
                operation("removeFile" + type, "Remove a file from a record", null)
                    .addParametersItem(idParameter())
                    .addParametersItem(fileId)
                    .responses(responses("ResponseVoid", "200", "Removed", "401", "403", "404"))));
  }

  // The record as the API returns it: the fields of the entity and the columns the core keeps.
  private Schema<?> record(Model model, EntityMeta entity) {
    ObjectSchema schema = new ObjectSchema();
    schema.addProperty("id", new IntegerSchema().format("int64").readOnly(true));
    for (FieldMeta field : entity.fields()) {
      if (!"file".equals(field.type())) {
        schema.addProperty(field.name(), labelled(fieldSchema(model, field), field.label()));
      }
    }
    schema.addProperty("created_by", new StringSchema().readOnly(true));
    schema.addProperty("created_at", new DateTimeSchema().readOnly(true));
    schema.addProperty("updated_at", new DateTimeSchema().readOnly(true));
    schema.addProperty("version", new IntegerSchema().format("int64").readOnly(true));
    if (entity.hasProcess()) {
      List<String> states = entity.process().states().stream().map(state -> state.name()).toList();
      schema.addProperty("status", new StringSchema()._enum(states).readOnly(true));
      schema.addProperty("assignee", new StringSchema().nullable(true).readOnly(true));
      schema.addProperty("decided_by", new StringSchema().nullable(true).readOnly(true));
      schema.addProperty("decided_at", new DateTimeSchema().nullable(true).readOnly(true));
      schema.addProperty("handle_by", new DateTimeSchema().nullable(true).readOnly(true));
      schema.addProperty("overdue", new BooleanSchema().readOnly(true));
      schema.addExtension(
          "x-dxos-process", Json.mapper().convertValue(entity.process(), Object.class));
    }
    schema.description(entity.label());
    return schema;
  }

  // What a caller may send: the fields of the entity, and version when changing a record.
  private Schema<?> input(Model model, EntityMeta entity) {
    ObjectSchema schema = new ObjectSchema();
    List<String> required = new ArrayList<>();
    for (FieldMeta field : entity.fields()) {
      if ("file".equals(field.type()) || "code".equals(field.type())) {
        continue;
      }
      schema.addProperty(field.name(), labelled(fieldSchema(model, field), field.label()));
      if (field.required()) {
        required.add(field.name());
      }
    }
    schema.addProperty(
        "version",
        new IntegerSchema()
            .format("int64")
            .description("When changing a record: the version read with it"));
    if (!required.isEmpty()) {
      schema.required(required);
    }
    return schema;
  }

  // The label of the field, then what the type itself says about it.
  private static Schema<?> labelled(Schema<?> schema, String label) {
    String own = schema.getDescription();
    schema.setDescription(own == null ? label : label + ". " + own);
    return schema;
  }

  private Schema<?> fieldSchema(Model model, FieldMeta field) {
    Schema<?> schema =
        switch (field.type()) {
          case "number" -> numberBounds(new NumberSchema(), field);
          case "boolean" -> new BooleanSchema();
          case "date" -> dateBounds(new DateSchema(), field);
          case "datetime" -> new DateTimeSchema();
          case "choice" -> options(field, field.options());
          case "unit" -> options(field, model.units().stream().map(Model.Unit::name).toList());
          case "reference" ->
              new IntegerSchema()
                  .format("int64")
                  .description("The id of a record of " + field.reference());
          case "user" -> new StringSchema().description("The id of an account");
          case "code" -> code(field);
          default -> text(field);
        };
    if (field.requiredWhen() != null) {
      schema.addExtension(
          "x-dxos-required-when", Json.mapper().convertValue(field.requiredWhen(), Object.class));
    }
    return schema;
  }

  private Schema<?> code(FieldMeta field) {
    StringSchema schema = new StringSchema();
    schema.readOnly(true);
    schema.description("Assigned when the record is created, in the form " + field.format());
    schema.addExtension("x-dxos-code-format", field.format());
    return schema;
  }

  // One of the options, or several of them listed once each.
  private Schema<?> options(FieldMeta field, List<String> values) {
    StringSchema one = new StringSchema();
    one._enum(values);
    if (!field.multiple()) {
      return one;
    }
    return new ArraySchema().items(one).uniqueItems(true);
  }

  private Schema<?> text(FieldMeta field) {
    StringSchema schema = new StringSchema();
    if (field.pattern() != null) {
      // The whole value must match, so the pattern is anchored the way the database does it.
      schema.pattern("^(?:" + field.pattern() + ")$");
    }
    return schema;
  }

  private Schema<?> numberBounds(NumberSchema schema, FieldMeta field) {
    if (field.min() != null) {
      schema.minimum(new java.math.BigDecimal(field.min()));
    }
    if (field.max() != null) {
      schema.maximum(new java.math.BigDecimal(field.max()));
    }
    return schema;
  }

  // OpenAPI has no minimum for a date, so the bounds are in the description and an extension.
  private Schema<?> dateBounds(DateSchema schema, FieldMeta field) {
    if (field.min() != null) {
      schema.addExtension("x-dxos-min", field.min());
    }
    if (field.max() != null) {
      schema.addExtension("x-dxos-max", field.max());
    }
    if (field.min() != null || field.max() != null) {
      schema.description(
          "On or after "
              + (field.min() == null ? "any date" : field.min())
              + " and on or before "
              + (field.max() == null ? "any date" : field.max()));
    }
    return schema;
  }

  private List<Parameter> listParameters(EntityMeta entity) {
    List<Parameter> parameters = new ArrayList<>(filterParameters(entity));
    List<String> sortable = new ArrayList<>(List.of("id", "created_at", "updated_at"));
    entity.fields().stream()
        .filter(f -> !"file".equals(f.type()))
        .map(FieldMeta::name)
        .forEach(sortable::add);
    if (entity.hasProcess()) {
      sortable.add("status");
    }
    parameters.add(new QueryParameter().name("sort").schema(new StringSchema()._enum(sortable)));
    parameters.add(
        new QueryParameter()
            .name("order")
            .schema(new StringSchema()._enum(List.of("asc", "desc"))));
    parameters.add(
        new QueryParameter()
            .name("page")
            .schema(new IntegerSchema().minimum(java.math.BigDecimal.ZERO))
            .description("From 0"));
    parameters.add(
        new QueryParameter()
            .name("size")
            .schema(
                new IntegerSchema()
                    .minimum(java.math.BigDecimal.ONE)
                    .maximum(java.math.BigDecimal.valueOf(200))));
    return parameters;
  }

  private List<Parameter> filterParameters(EntityMeta entity) {
    List<Parameter> parameters = new ArrayList<>();
    for (FieldMeta field : entity.fields()) {
      if (!"file".equals(field.type())) {
        parameters.add(
            new QueryParameter()
                .name("filter." + field.name() + (field.multiple() ? ".contains" : ""))
                .description(filterText(field))
                .schema(filterSchema(field)));
      }
    }
    return parameters;
  }

  // What a field can be filtered by: a field with several values only by one option it holds, a
  // date by equality and a range, a flag by equality, and the rest by the general comparisons.
  private static String filterText(FieldMeta field) {
    String name = "filter." + field.name();
    if (field.multiple()) {
      return "Rows holding the option; only " + name + ".contains is accepted";
    }
    return switch (field.type()) {
      case "boolean" -> "Rows with this value; " + name + "=true or " + name + "=false";
      case "date" -> "Rows on the date; " + name + ".gte and " + name + ".lte give a range";
      case "text", "choice", "user", "unit" ->
          "Rows whose value equals; " + name + ".<op> takes eq, ne, lt, lte, gt, gte, like or in";
      default -> "Rows whose value equals; " + name + ".<op> takes eq, ne, lt, lte, gt, gte or in";
    };
  }

  private Schema<?> filterSchema(FieldMeta field) {
    if (field.multiple()) {
      return new StringSchema();
    }
    return switch (field.type()) {
      case "boolean" -> new BooleanSchema();
      case "number" -> new NumberSchema();
      case "date" -> new DateSchema();
      case "datetime" -> new DateTimeSchema();
      default -> new StringSchema();
    };
  }

  // Sending the same key with the same request again returns the first answer and does nothing
  // more.
  private Parameter idempotencyKey() {
    return new HeaderParameter()
        .name("Idempotency-Key")
        .required(false)
        .description(
            "A key of 1 to 128 characters from A-Z a-z 0-9 . _ : - chosen by the caller."
                + " The same key with the same request returns the first answer with the header"
                + " Idempotent-Replayed: true and does nothing again; the same key with another request"
                + " gives 409 with data.reason IDEMPOTENCY_KEY_REUSED. A failed request keeps no key,"
                + " and a key is kept for 24 hours.")
        .schema(new StringSchema().minLength(1).maxLength(128).pattern("^[A-Za-z0-9._:-]+$"));
  }

  // The answer to a repeated request says so in a header.
  private ApiResponses replayable(ApiResponses responses) {
    responses
        .get("200")
        .addHeaderObject(
            "Idempotent-Replayed",
            new Header()
                .description(
                    "true when this is the answer kept for an earlier request with the"
                        + " same Idempotency-Key")
                .schema(new StringSchema()._enum(List.of("true"))));
    return responses;
  }

  private Parameter idParameter() {
    return new PathParameter().name("id").schema(new IntegerSchema().format("int64"));
  }

  private Operation operation(String id, String summary, String description) {
    Operation operation = new Operation().operationId(id).summary(summary);
    if (description != null) {
      operation.description(description);
    }
    return operation.addTagsItem("data");
  }

  private RequestBody body(String schema) {
    return body(schema, true);
  }

  private RequestBody body(String schema, boolean required) {
    return new RequestBody()
        .required(required)
        .content(new Content().addMediaType(JSON, new MediaType().schema(ref(schema))));
  }

  // The first status is the one a call answers with; the others are the errors it can give.
  private ApiResponses responses(
      String schema, String success, String description, String... errors) {
    ApiResponses responses = new ApiResponses();
    responses.addApiResponse(
        success,
        new ApiResponse()
            .description(description)
            .content(new Content().addMediaType(JSON, new MediaType().schema(ref(schema)))));
    for (String code : errors) {
      responses.addApiResponse(code, errorResponse(code));
    }
    // Any call can be refused for coming too fast.
    responses.addApiResponse("429", errorResponse("429"));
    return responses;
  }

  private ApiResponse errorResponse(String code) {
    return new ApiResponse()
        .description(errorText(code))
        .content(new Content().addMediaType(JSON, new MediaType().schema(ref("ApiError"))));
  }

  private String errorText(String code) {
    return switch (code) {
      case "400" -> "The body or a parameter is not valid; data names each field and why";
      case "401" -> "No valid token";
      case "403" -> "The caller may not do this";
      case "404" -> "No such record";
      case "409" ->
          "The record is in another state, changed in between, is kept by its process"
              + " or is still referred to, or the slot or the unique value is taken";
      case "422" -> "The file is infected and was refused (ERR_FILE_INFECTED)";
      case "429" -> "Too many calls in a short time (ERR_TOO_MANY_REQUESTS); try again later";
      case "503" -> "Files cannot be checked right now (ERR_SCAN_UNAVAILABLE); try again later";
      default -> "Error";
    };
  }

  private Schema<?> envelope(Schema<?> data) {
    return new ObjectSchema()
        .addProperty("success", new BooleanSchema())
        .addProperty("message", new StringSchema())
        .addProperty("data", data)
        .addProperty("timestamp", new DateTimeSchema());
  }

  private Schema<?> ref(String name) {
    return new Schema<>().$ref(SCHEMAS + name);
  }

  private static String label(EntityMeta entity) {
    return entity.label() == null ? entity.name() : entity.label();
  }

  private static String pascal(String name) {
    StringBuilder out = new StringBuilder();
    for (String part : name.split("[^A-Za-z0-9]+")) {
      if (!part.isEmpty()) {
        out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
      }
    }
    return out.toString();
  }
}
