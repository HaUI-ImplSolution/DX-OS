# The model file

A YAML file that declares the entities, fields, roles, permissions and workflow of a business.
The core builds the data tables, the Keycloak roles, the API and the interface from this file, so changing the business means changing the file, not the code.
The current specification for editors is [`model-v1.schema.json`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/backend/dx-model/src/main/resources/schema/model-v1.schema.json), and the wrongly formed files used by the automatic checks are in [`e2e/fixtures/invalid-models/`](https://github.com/HaUI-ImplSolution/DX-OS/tree/main/e2e/fixtures/invalid-models).

## Example

Shortened from [`equipment.yaml`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/backend/dx-engine/src/main/resources/templates/equipment.yaml):

```yaml
format: 1
name: equipment
roles:
  - {name: BORROWER}
  - {name: KEEPER, staff: true}
entities:
  device:
    fields:
      code: {type: text, required: true, unique: true}
      name: {type: text, required: true}
    access: {BORROWER: read, KEEPER: all}
  loan:
    fields:
      device: {type: reference, to: device, required: true}
      reason: {type: text, required: true}
      from_at: {type: datetime, required: true}
    access: {BORROWER: own, KEEPER: all}
    process: {family: approval, handler: KEEPER}
```

The templates you can use are in [`templates/`](https://github.com/HaUI-ImplSolution/DX-OS/tree/main/backend/dx-engine/src/main/resources/templates) and show on the Template library tab of the control panel.

## Upgrading the format

The current format is `1`.
The core still reads format `0` by running the migration `0 → 1` in memory before it checks the file; a format newer than the core is refused.

`dxos model upgrade <file>` calls the same migration pipeline and prints a unified diff without writing the file.
After reading the diff, run `dxos model upgrade --write <file>` to replace the file with the complete result through one atomic rename.
Running it again on the current format changes no content.
The system recognizes that a file changed by its text, so the first apply of an upgraded file creates one more version that changes no table or permission, and `dxos plan --check` returns 2 until then.

The temporary file sits in the same folder as the original file and keeps the mode, the owner and the group of the file.
An error in the migration or in writing the temporary file leaves the original file as it is; if the process stops before the rename, delete the leftover `.dxos-model-upgrade.*` file and run it again.
To go back after the rename, use the Git history or a backup.

## Extending a template

A file starts from a template of the library with the top-level key `extends` and writes only what differs from the template:

```yaml
extends: asset_register
name: school_assets
roles:
  - name: AUDITOR
    description: Kiểm kê
entities:
  asset:
    fields:
      price: null
      serial: {type: text, label: Số seri, unique: true}
    access: {CUSTODIAN: all, AUDITOR: read}
```

The merge rules:

- `name` is always written in the file; `format` may be left out, and if written it must equal the format of the template.
- A mapping merges key by key, and a key set to `null` removes the key of the template.
- `roles` and `units` merge by `name`: an item with the same name merges key by key, a new item is appended after the items of the template; a role or unit of the template cannot be removed.
- Every other list (`options`, `columns`, `groups`, statuses, actions) is replaced whole by the list of the file.
- The merged result is checked as a complete file; removing a field that is still mentioned elsewhere gives an error with the line number of the merged file.

`dxos plan` and `dxos apply` merge at run time, and `dxos model merge <file>` prints the complete file for comparison.
A template name that is not in the library is refused, and the merged file is saved with the name and the hash of the template.
`dxos lint` checks only the part of the short file (YAML, `extends` is text, has a `name`) and reports `ok (extends <template>)`; the part merged with the template is checked by `dxos plan`.

## Checking a file before applying

`dxos lint <file>...` reads the file with the very reader of the backend and needs no running system.
Each error prints one line `file:line:column: message`, or `file: message` when the error has no single position.
The exit code is 0 when every file is valid, 1 when there is an error and 2 when a file cannot be read.
On an install the command runs in the installed backend image, opens no network and changes no file.
On a machine without DX-OS, download `dxos-lint.jar` from the Release page and run `java -jar dxos-lint.jar <file>...` with Java 21; if you set `DXOS_LINT_JAR`, `dxos lint` uses this file instead of the image.
An example CI workflow for a model repository is in [`examples/model-repo`](https://github.com/HaUI-ImplSolution/DX-OS/tree/main/examples/model-repo).

A key written twice in the same block is refused, with the line and column of the second one (`key BORROWER is written twice`), so that a permission row or a field does not vanish quietly.
A model saved earlier that has a duplicate key still runs, the later key is used, and a warning shows on the Running models tab; edit the file and apply again to clear the warning.

Lint checks only the file, so `dxos plan` also compares the file with the running model, the webhook target and the existing data.
A valid lint result does not mean `apply` succeeds; a lint result with errors means `plan` refuses exactly those errors too.

`dxos conformance <model>` runs on a running system and checks the guard rails of the core for the applied model; how to run it and its conditions are in [Install and operations](../OPERATIONS.md#kiểm-tệp-mô-hình-và-rào-chắn-của-lõi) (Vietnamese).

## Fields and units

- The field types: `text`, `number`, `datetime`, `date`, `boolean`, `choice`, `reference`, `user`, `unit`, `code` (the section [Readable record codes](#readable-record-codes)).
- A `date` field holds a day in the form `YYYY-MM-DD`, while `datetime` holds a moment with a time zone.
- A `boolean` field is true or false (`true`, `false`); do not set `required` or `unique`, and set `default` if you want an initial value.
- A `choice` field with `multiple: true` holds several choices from `options`.
  The API accepts and returns an array, each choice once and in the order of `options`, and filters with `filter.<name>.contains=<choice>`.
  `default` is a list of choices from `options`, with no duplicates and not empty, for example `default: [Mới, Đã sửa]`; a record created without sending this field gets that list.
  This field does not go with `unique`.
- A `reference` field points to an entity of the same model.
  The record picker in the interface searches by part of the `text` field that names the target record (`name`, `title` or `code`, or else the first `text` field) with `filter.<field>.like`, ignoring case but needing the right accents.
  A target entity without a `text` field makes the picker search by the number `id`.
- A `user` field holds the id of an account; the interface lets you choose by name, and the API refuses an id that does not exist.
- Units are declared in `units`, each unit being a Keycloak group; a field of type `unit` tells which unit a record belongs to.
  A complete example is in [`onestop.yaml`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/backend/dx-engine/src/main/resources/templates/onestop.yaml).

## Readable record codes

A field with `type: code` gives each record a short code for reading, printing and lookup, for example `HS-2026-00007`.

```yaml
fields:
  ma_ho_so: {type: code, format: "HS-{year}-{seq:5}"}
```

- `format` is made of fixed text (letters, digits and `.` `_` `/` `-`), `{year}` is the year the record was created (4 digits, Vietnam time) and exactly one `{seq:N}` is the sequence number written in full with `N` digits, from 1 to 9, padded with leading zeros.
- Each entity has at most one `code` field; this field is always unique and does not go with `required`, `unique`, `default`, `renamed_from` or value constraints.
- The system assigns the code once when the record is created; writing or editing the code through the API is refused (400), and editing the record does not change the code.
- The sequence number counts separately for each model, entity and field; a format with `{year}` restarts from 1 each year, and a format without it keeps counting forever.
  Two records created at the same time never share a code, and a record that fails to be created does not waste a number.
- When the numbers run out (for example `{seq:3}` has reached 999), a new record is refused with 422 `ERR_CODE_EXHAUSTED`; raise `N` in the format.
- Look up by code with `filter.<field>=<code>` or `filter.<field>.like=<part of the code>`; the metadata records `type: code` with `format`, and the OpenAPI of the model marks the field read-only.
- Adding a `code` field to an entity that already has records is a `MIGRATION` change: at apply the old records receive codes in the order of `id`, counted by the year the record was created, and new records carry on from there.
- Changing `format` is a `SAFE` change: only new records follow the new format, the numbers carry on and old codes stay as they are.
- If you remove a `code` field and add it again (or add another field with the same name), the numbers still carry on from the last number used, because the counter is tied to the model, the entity and the field name.

## Value constraints

- `min` and `max` limit a `number` or `date` field, and both ends accept the value: `qty: {type: number, min: 1, max: 100}`.
- `pattern` is the regular expression of a `text` field, and must match the whole value: `code: {type: text, pattern: "[A-Z]{3}-[0-9]+"}`.
  The expression may contain only characters, the dot, the character class `[...]`, the group `(...)`, `|` and the quantifiers `*`, `+`, `?`, `{n}`, `{n,m}` with a repeat count of at most 255.
  There is no `^`, `$`, `\d`, `(?` or lazy quantifier.
  A group that has a quantifier inside must not be repeated with `*`, `+` or `{n,m}`, as in `([a-z]+)*`, because that way of writing can make the match run for a very long time.
  A value checked with `pattern` may be at most 1000 characters long, and the match may read only a certain number of characters; beyond that the value is refused with `is too complex to check against the pattern`.
- A value that violates a constraint is refused by the API with the field name, and the table has a `CHECK` constraint, so writing straight into the database is also blocked.
- Tightening or adding a constraint on a table that already has violating rows makes the plan refused and states the number of violating rows; loosening or removing a constraint puts the plan at level `SAFE`.

## Conditional required

`required_when` makes a field required only when another field of the entity has a certain value:

```yaml
note: {type: text, required_when: "kind == Phần mềm"}
```

- The condition is written like the `when` of an action: one comparison, or several comparisons joined with `and`, `or`, `not` and parentheses; `==` and `!=` for every type, `<`, `<=`, `>`, `>=` only for numbers and dates.
- The field compared is a `number`, `date`, `text`, single-choice `choice` or `boolean` (`foreign == true`) field of the same entity.
- The value of a `date` field is written like `YYYY-MM-DD`, for example `due >= 2026-01-31`; a date that does not exist is refused when the model is read.
- The API checks on create and on edit, on the record after the new values are merged; a field that is still empty is reported as `is required when kind == Phần mềm`.
- It does not go with `required`, is not put on a `boolean` or `file` field, and does not compare a field with itself, even inside a condition that joins several comparisons.
- It is checked only in the API; the database has no such constraint.

## The `ui` block

The `ui` block of an entity chooses how it appears on screen; without it the interface is built in the order of the fields:

```yaml
ui:
  icon: inventory
  menu: {group: Kho, order: 1}
  list: {columns: [code, kind, price], sort: -created_at}
  form:
    groups:
      - {label: Nhận dạng, fields: [code, kind]}
      - {label: Chi tiết, fields: [price, bought_on]}
  hints: {code: "Ba chữ in hoa, dấu gạch ngang, bốn chữ số"}
```

- `icon` is one of `description`, `folder`, `inbox`, `person`, `group`, `school`, `assignment`, `event`, `room`, `computer`, `build`, `inventory`, `payments`, `support`, `shield`, `settings`, `list`, `calendar`, `chat`, `local_shipping`.
- `menu.group` gathers entities into a group in the menu and `menu.order` is the order.
- `list.columns` are the columns of the list: fields of the entity, or `created_at`, `updated_at`, and `status`, `assignee` when the entity has a workflow.
  `list.sort` is a column name, with a leading `-` to sort descending.
  When it is not declared, the list opens by `-id`; an entity with a workflow opens by `-updated_at` and shows the column `updated_at`.
- `form.groups` split the form into labeled groups; a field that is in no group goes into the last group.
- `hints` set a hint line under the input box of a field.
- `ui` only chooses among what the interface already has and changes no permission; a wrong field, column or icon name makes the file refused with the line and column.
- The columns a user chooses with the Columns button are kept in the browser and override the default columns of `ui`.
- Changing `ui` is a `SAFE` change, touches no data, and can be applied even while the entity still has records being handled.

A complete example is in [`asset_register.yaml`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/backend/dx-engine/src/main/resources/templates/asset_register.yaml).

## Permissions by role

`access` gives each role a level:

| Level | Meaning |
|---|---|
| `all` | Every row, read and write |
| `own` | The rows of the person themselves |
| `read` | Every row, read-only |
| `unit` | The rows of the person's own unit |
| `assigned` | The rows assigned to the person |

Changing `access` touches no data, and the plan records each role whose level changes as a line `role: old → new`.
Granting more (`GRANT_ACCESS`) and widening (`WIDEN_ACCESS`) are at level `MIGRATION`, with a question naming the role and the entity, because the person who holds the role has the new permission as soon as it is applied; you need `--accept MIGRATION` or a confirmation on the Plan tab.
The other three kinds are at level `SAFE`.
That line belongs to one of five kinds: granting to a role that had none (`GRANT_ACCESS`), revoking (`REVOKE_ACCESS`), widening (`WIDEN_ACCESS`), narrowing (`NARROW_ACCESS`) and changing (`CHANGE_ACCESS`).
Widening or narrowing is judged on two things: the number of rows the role touches (`own` and `assigned`, then `unit`, then `all` and `read`) and the write permission (`read` cannot write).
A change that raises one and lowers the other, like `own` to `read`, or that neither raises nor lowers, like `own` to `assigned`, is recorded as a change.

`owner_via` lets a child entity follow the owner of its parent entity, across several levels.

## Workflow

- `handler` is the handling role, or a list of roles such as `[MANAGER, SELLER]`; whoever holds one of those roles can handle.
- `family` chooses a workflow family to start from: `approval`, `work_item` or `reservation`; left empty, you declare the full `states` and `actions` yourself.
- `deadline` is an optional handling deadline, a number with `m` (minutes), `h` (hours) or `d` (days), for example `72h`; add `calendar` to count by a working calendar ([Working calendars](#working-calendars)).
- `escalate` is a list of at most 5 escalation levels counted from the moment a record passes its `deadline`, so the workflow must declare a `deadline`.
  Each level declares `after` (the time after the deadline) and `to` (a role or a list of roles, each of which must be able to read the record), and `repeat: {every: 12h, times: 3}` if you want a repeated reminder; without `repeat` each level sends once.
  For example: `escalate: [{after: 24h, to: MANAGER}, {after: 72h, to: [DIRECTOR], repeat: {every: 12h, times: 3}}]`.
  Escalation only writes an `escalated` history row and sends a notification through the webhook of the model; it changes no status, no holder of the record and no one's permission.
  When the service is switched off and on again, only the latest notification that has fallen due is sent, and a record that enters a new deadline is counted from the start again.
- The families require a reason when rejecting and returning; a workflow you declare yourself sets `needs_note: true` on each action that needs a reason.
- The submitter can withdraw a record only in the statuses declared in `withdraw`; a workflow that does not declare `withdraw` does not allow withdrawing.
- A record with a workflow cannot be deleted, a record that has been decided cannot be edited, and a record that has been assigned can be handled only by the assignee.
- The creator of a record cannot decide on it, unless `allow_self_decision: true` is declared.

### Declaring or extending a workflow yourself

- `states` is a map from a status name to `{label, terminal}`; `initial` is the status a new record enters, by default the first declared status.
- `actions` is a map from an action name to `{label, from, to, by, role, needs_note, require, when, check}`.
  `from` is one status or a list, `by` is `handler` (the default) or `owner`.
  `role` limits an action of the handler to a role of its own instead of all of `handler`.
  The role written in `role` need not be in `handler`; whoever holds that role can still perform the action.
  When the button `label` is empty, the interface shows the code of the action.
  `needs_note: true` requires a reason.
  `require` lists the fields that must have a value before the action can be performed.
  `when` is a condition on the fields of the record: one comparison of a field with a value, written as the string `field operator value`, for example `when: "amount > 5000000"`, or several comparisons joined with `and`, `or`, `not` and parentheses.
  The action can be performed only when the condition is true for the record at the moment of acting.
  The operator is `==`, `!=`, `<`, `<=`, `>` or `>=`.
  The field is a `number`, `date`, `choice`, `text` or `boolean` field of the entity; `<`, `<=`, `>` and `>=` are used only for `number` and `date` fields.
  The value of a `number` field is a number, of a `date` field a date `YYYY-MM-DD`, of a `choice` field one of its `options`.
  A field with no value fails every comparison, so the `not` of that comparison is true.
  Joining several comparisons: `when: "amount > 5000000 and (kind == book or kind == pen)"`.
  `not` is evaluated before `and`, `and` before `or`, and parentheses change the order.
  When several comparisons are joined, a value that has spaces or parentheses or is `and`, `or` or `not` goes in quotes, for example `kind == "Phần mềm"`; a single comparison needs no quotes.
  A condition has at most 8 comparisons and parentheses nested at most 3 levels; wrong syntax is refused with the line, the column and the place of the error.
  `when` is used only for the actions of the handler, so it cannot be used with `withdraw` or with the action of `auto_close`.
  Write `when: null` to remove the condition of an action in a family.
  An action whose condition is not met returns 409 `ERR_INVALID_STATE` with `data.reason` `CONDITION_NOT_MET`.
  `check: <target name>` lets a separate service decide the action before the record changes status.
  The target name is declared in `deploy/webhooks/targets.yml` like the target of `webhook`; a name that is not in the file makes the model refused.
  The service is asked after every rule of the workflow has been met and before the status changes.
  A refused action returns 403 `ERR_ACTION_NOT_ALLOWED` with `data.reason` `CHECK_DENIED` and `data.code` the code that the service returned.
  When the service does not answer or answers wrongly, what happens is decided by the `on_error` of the target; how to declare it and the protocol are in [Integrating with outside systems](./INTEGRATIONS_EN.md#checking-before-a-status-change).
  The automatic closing action of `auto_close` does not ask the service.
- When there is a `family`, the declared entries override the keys of the workflow template one by one; write `null` to remove a status or an action of the template.
- `withdraw` and the status `withdrawn` are reserved names: declaring `withdraw: {from: [...]}` makes the platform add the status `withdrawn` by itself, and this action is always performed by the submitter.
- The action names `created`, `updated`, `deleted`, `assigned`, `file_added`, `file_removed`, `completion_skipped`, `auto_closed`, `overdue`, `due_soon`, `escalated`, `check_denied`, `check_unavailable` and `check_skipped` are reserved, because the record history uses them for the rows the platform writes.
- Each status that is not final must have an action going out, and at least one action going out with no `when`, so a record always has a way forward; every status must be reachable from `initial`, a final status has no action going out, and there must be at least one final status.
- `auto_close` is the time to wait for the submitter before the system performs an action in their place.
  The `work_item` family accepts the short form `auto_close: 7d` (the action `confirm`); in other cases write `auto_close: {after: 7d, action: <an action of the submitter>}`.
  The action of `auto_close` must not set `needs_note: true`, because the system does not write a note; a file that violates this is refused on checking, while a saved version that violates it runs with `auto_close` dropped and a warning (see [Install and operations](../OPERATIONS.md#mô-hình-đã-lưu-không-còn-qua-bộ-kiểm), Vietnamese).
- A workflow that differs from the original template runs on its own BPMN, generated from the declared statuses and actions when the model is applied.
- Changing the first status, adding or removing a status, or changing `from`, `to`, `by` or `when` of an action while records are not yet in a final status makes the plan refused.
- Changing a label, `role`, `require`, the statuses of `withdraw` or the list `handler` is safe and takes effect at once, even for records that are open.
- Adding `require` to an action is still a safe change, but a record waiting in the source status of the action that still lacks that field (empty or only spaces also counts as missing) is refused by the action until it is filled in; the plan names the action, the field and the number of such records in `impacts`.
- Removing a status that still has records in it, or removing `terminal` from a status that still has closed records, is also refused, because a closed record cannot continue.

```yaml
process:
  handler: [MANAGER, SELLER]
  states:
    todo: {label: Mới}
    review: {label: Đang xem}
    done: {label: Xong, terminal: true}
  actions:
    take: {from: todo, to: review, role: SELLER}
    finish: {from: review, to: done, role: MANAGER, require: [amount]}
    withdraw: {from: todo}
```

Two actions whose `when` conditions exclude each other split the records by the value of a field; an action with no `when` is always a way out:

```yaml
states:
  lead_approved: {label: Chờ giám đốc}
actions:
  lead_approve: {from: submitted, to: approved, role: LEAD, when: "amount <= 5000000"}
  lead_forward: {from: submitted, to: lead_approved, role: LEAD, when: "amount > 5000000"}
  lead_reject: {from: submitted, to: rejected, role: LEAD, needs_note: true}
  approve: {from: lead_approved, to: approved, role: DIRECTOR}
```

### Working calendars

`calendars` at the top of the file declares working calendars, and a workflow that has a `deadline` chooses one with `calendar: <name>`.
A workflow that declares no `calendar` counts the deadline continuously, day and night.

```yaml
calendars:
  office:
    days: [mon, tue, wed, thu, fri]
    hours: ['08:00-12:00', '13:30-17:30']
    holidays: [2026-12-25]
entities:
  leave_request:
    process: {family: approval, handler: MANAGER, deadline: 2d, calendar: office}
```

- `days` are the working days of the week (`mon` to `sun`) and at least one day is required.
- `hours` is a list of time ranges `HH:MM-HH:MM` in time order, neither touching nor overlapping, and `24:00` is used only as an end time; without `hours`, the whole day is working time.
- `holidays` is a list of days off (`2026-12-25`), at most 1000 days.
- Times follow the time zone `Asia/Ho_Chi_Minh`.
- A `deadline` in `m` or `h` counts only through working hours; in `d` it is that many working days, landing on the same time of day as the start.
- A record that starts waiting outside working hours is counted from the next opening; on Friday at 16:00 with a deadline of `1d`, it ends at 16:00 on Monday.
- The deadline of a record (`handle_by`) is computed once when the record enters the waiting status, so changing the calendar does not move the deadline of a record that already exists.
- `due_soon` and `escalate` still count in ordinary hours from that deadline, so a notification can fall on a day off.
- Changing the calendar of a workflow, or changing the hours, working days or days off of a calendar, is a `SAFE` change (`CHANGE_CALENDAR`) and is refused while records are open, like changing `deadline`.
- A calendar that no workflow uses is still saved, and `extends` merges `calendars` by calendar name.

### Booking

`family: reservation` (which can be extended as above) with `booking: {resource, starts, ends, capacity}`.
A booking that makes the number of runs at the same time exceed the capacity is refused, and the application has a calendar view.
The calendar loads every booking the viewer can see in the interval being viewed, 200 per page and at most 5000; for a larger interval the calendar reports the number shown out of the total and tells you to move to a shorter interval.
An example is in [`room_booking.yaml`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/backend/dx-engine/src/main/resources/templates/room_booking.yaml).

### Completion actions

`after` assigns a list of operations to a final status, for example `after: {approved: [{do: add_to_group, user: created_by, group_from: nhom}]}`.
The operations run after the transaction ends and are retried on error.
The operations available are `add_to_group` and `remove_from_group`.
The Keycloak group must already exist; if it does not, the message can be seen on the Event queue tab.
When the user or group field is empty, the operation is skipped and the record gets one more history row `completion_skipped` with the reason.
An example is in [`itaccess.yaml`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/backend/dx-engine/src/main/resources/templates/itaccess.yaml).

## Print documents

The `documents` key of an entity declares print templates; each template turns a record into a PDF file.
A template has a name (`[a-z][a-z0-9_]*`), and the keys `label`, `file`, `when`, `title` and `body`.

```yaml
documents:
  confirmation:
    label: Giấy xác nhận
    file: giay-xac-nhan-{{id}}
    when: approved
    title: GIẤY XÁC NHẬN
    body:
      - heading: Thông tin chung
      - text: "Xác nhận {{full_name}} đã nộp hồ sơ số {{id}}."
      - fields: [full_name, created_at]
      - signatures: [Người nộp, Cán bộ phụ trách]
```

- `label` is the name of the template on the record screen; it has no blanks.
- `file` is the name of the downloaded file without `.pdf`; accented letters become unaccented, and characters other than letters, digits, `.`, `-` and `_` become `_`.
- `when` is a status or a list of statuses of the workflow that the record must be in to be printed; leave it out to print in every status, and an entity with no workflow does not use `when`.
- `title` is printed in the middle of the top of the first page.
- `body` is a list of blocks, each block having exactly one key:
  - `heading`: a bold heading line.
  - `text`: text; each line break is a line of the page and a blank line is a gap.
  - `fields`: a table of labels and values for each field that is named; an empty value prints `—`.
  - `signatures`: from one to four signing titles, set side by side above a blank space to sign.

A blank `{{name}}` in `file`, `title`, `heading`, `text` and `signatures` is replaced by the value of the record.
The name is a field of the entity, or a column that every record has: `id`, `created_at`, `updated_at`, and for an entity with a workflow also `status` and `decided_at`.
A template has no conditions, loops or expressions, and reads no data other than the record being printed.

Values print as follows: a date as `dd/MM/yyyy`, a moment as `dd/MM/yyyy HH:mm` in the time zone of the server, true or false as `Có` or `Không` (Yes or No), a status by its label, a unit by the unit label, a multiple-choice field separated by commas.
A reference field and a user field print their stored value.
The labels of the columns `id`, `status`, `created_at`, `updated_at` and `decided_at` in the table are `Mã`, `Trạng thái`, `Ngày tạo`, `Cập nhật lần cuối` and `Ngày quyết định`; the printed text is in Vietnamese.

Each entity has at most 10 templates, each template at most 60 blocks, each text at most 2000 characters and each document at most 50 pages.
`plan` and `apply` refuse a template that has a blank that is not a field, a status that is not in the workflow, an unknown block or one over the limits, with the line and column.
A changed template is a change of level `SAFE` (`CHANGE_DOCUMENTS`).

`GET /api/v1/data/{model}/{entity}/{id}/documents` lists the templates of the record, the file names and whether the record can be printed in the current status.
`GET /api/v1/data/{model}/{entity}/{id}/documents/{name}` returns the PDF file as an attachment.
Both are for people who can read the record, like the record screen; a template outside the allowed statuses returns 409 with the reason `DOCUMENT_NOT_AVAILABLE`, and each print is written to `audit_log` (`DOCUMENT_PRINTED`).

## Attachments

A field `type: file`; upload with `POST /api/v1/data/{model}/{entity}/{id}/files`, with permissions following those of the record.
The file types accepted are set with `engine.files.allowed-extensions`, and a file is always downloaded as an attachment.
A RAR file is always refused, even when renamed or when the list of file types is empty, because the project's ClamAV cannot open RAR.
Each record holds at most 20 files, set with `engine.files.max-per-record`.

## Webhook

`webhook: <target name>` at the top of the file chooses where the record history of the model goes.
The address and the secret of the target are in `deploy/webhooks/targets.yml`, kept out of git; the sample is in `deploy/webhooks/targets.example.yml`.
A model that does not declare one uses `DXOS_WEBHOOK_URL`, and a target name that is not in the file makes the model impossible to apply.
Each target has `format: json` (the default) or `format: chat`.
The message of an `escalated` row also carries `level` (the level, from 1) and `round` (the reminder, from 0), and a chat message names the receiving roles.

## Changing a model that already has data

- `renamed_from` declares the old name when you rename a field, to keep the data.
- `default` fills in a value for a new required field on a table that already has rows; a multiple-choice field takes a list.
- Changing the type of an existing field (even from single choice to multiple choice) is refused; add a new field instead of changing the type.
- Renaming a field that has `min`, `max` or `pattern` keeps the data and renames its constraints too.
- Preview, apply, rollback and the related commands are in [Install and operations](../OPERATIONS.md#cài-đặt-và-lệnh-dxos) (Vietnamese) and [API](./API_EN.md).

## Business assistant tasks

Each entity can declare from one to eight tasks under `ai.tasks`, with the key as the task name.
Each task needs `classifier`, `input_fields` and `output_field`.
`label` is the task name by default.
The key `classifier` is required, matches `^[a-z][a-z0-9_]{0,39}$` and is the name of a weights file that the assistant has loaded.
The assistant has `onestop`, `support` and `clinic` ready.
For other businesses, follow the [guide to adding a classifier](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/tools/classifier/README.md#thêm-bộ-phân-loại-cho-nghiệp-vụ-mới) (Vietnamese).
`input_fields` holds only `text` fields, in the order used when training.
`output_field` is a single-choice `choice` field whose set of choices equals the business label set of the classifier.

```yaml
ai:
  tasks:
    classify_kind:
      label: Gợi ý loại hồ sơ
      classifier: onestop
      input_fields: [reason]
      output_field: kind
```

The output field of a task must not be used to grant group membership.
Declaring a task grants no extra permission; the person who confirms must still have the permission to edit the record.
Adding, changing or removing a task shows in the plan with the kind `CHANGE_AI_TASKS`.
When the assistant has loaded the weights for the task, the **Review record classifications** button in the Inbox reviews up to 25 eligible records on the page per round through `POST /assistant-api/v1/me/reviews`.
A record whose value differs from the suggestion is marked **Needs review**, with the suggested value and the words used as evidence.
Clicking the **Needs review** mark opens the assistant block on the detail page and creates a suggestion for the matching task so that the user can check and confirm it.
The assistant service runs in full mode, see [`assistant/README.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/assistant/README.md) (Vietnamese).
