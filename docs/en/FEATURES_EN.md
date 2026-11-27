# Features

The full list of what DX-OS can do, written in plain words.
Each feature is matched with its test in the [evidence matrix](https://haui-implsolution.github.io/DX-OS/docs/evidence-matrix) (Vietnamese); the known limits are in [`BACKLOG.md`](../dynamic-core/BACKLOG.md) (Vietnamese).

## Model declaration

- **From one file to a working system.**
  Records, data tables, accounts, roles, list screens and forms are all built from the model file.
  A file written in the old style still runs, and there is a command that brings it to the new style after previewing what will change.
- **Readable record codes.**
  The file declares a code pattern such as a prefix, the year and a sequence number.
  Each record gets a unique code, even when many people submit at the same time.
- **Data rules in the file itself.**
  True/false, dates, multiple choices, value limits, string patterns and conditional required fields are all declared in the file.
  The database also refuses a wrong value.
- **A customizable workflow.**
  Pick one of three ready templates: approval, a piece of work to handle, or booking by time slot with a capacity check.
  Add or remove steps and give them to a role of their own, or declare the workflow from scratch.
  A step can open only when the record meets a condition; rejecting and returning both need a reason; nobody approves their own record.
- **Change a business that already has data.**
  Preview the impact, a risk warning, keeping the data when a field is renamed, and going back to the previous version.

## Permissions and traces

- **Permissions down to the row.**
  Each role sees and edits only its own work, its unit's work, or work assigned to it.
- **A trace that cannot be edited.**
  The history of a record is chained into one chain with a check mark.
  Whoever edits, deletes or inserts a row, one command points to that exact row.
- **Data catalog.**
  One page tells where the data of each model lives, who may see it, and how the kinds of record relate to each other.
  The page also shows where data comes in and goes out down to each field, and how complete and fresh the data is.
- **Archiving old records.**
  Records closed long ago are written to an archive on a daily schedule.
  The data in use stays as it is, the archive can be checked again and queried directly with DuckDB.

## Extension and connection

- **Connect outward.**
  Every change of a record is reported to a web address or a chat channel (Mattermost, Slack).
  The notice has a signature so the receiver can verify it, and it is sent again when the receiver fails.
- **Open to outside applications.**
  Each model has its own API documentation that follows the model by itself.
  An outside service registers with exactly one role and a secret of its own.
  An interface on another domain signs users in by the OIDC standard (the Authorization Code flow with PKCE).
- **A control panel for the deployer.**
  A template library, a model editor, plan preview, apply, history, user management and record statistics.
  The editor lets you write the file directly or build it with a form that has a tree, a permission table and a workflow diagram you click to edit.
  It has a guide that says what you can write and extend, and a workflow diagram drawn from the file being edited.
- **Import records from a spreadsheet.**
  One command loads the rows of a CSV file as records.
  Each row goes through the same permissions, rules and workflow as a record typed by hand; a wrong row is reported with a reason.
  Sending the same file again creates no duplicate records.
- **Receive device readings over MQTT (optional).**
  Add a broker and a small bridge, and a device message becomes a record through exactly the permissions and workflow of the core.
  A message sent twice creates only one record, and when the core is down the messages are kept.
- **Ready-made metrics (optional).**
  Switch on a Prometheus that is already wired to the metrics of the core about records, deadlines, the event queue and model applies.
- **Group by project.**
  When several integrated systems share one install, models and outside applications are grouped by project to filter and see whom a change touches.
  A project is only a name for administration; it grants no permission and splits no data.
- **Administration by project.**
  Each project has a list of members and administrators.
  A project administrator assigns the roles of the models in the project to their own members without needing to administer the whole system.
  This is a division of administration, not a separation of data between organizations.
- **Print documents from a record.**
  The model file declares the template of a certificate, slip or minutes.
  A record becomes a Vietnamese PDF file for exactly the people who may see that record, and can be printed only in the statuses the template allows.

## Use and operation

- **An interface by declaration.**
  The model file chooses icons, menu groups, columns and the default sort, how a form is split into groups, and hints.
  An officer selects several records and handles them at once, and each record still goes through all the rules.
- **View by status and export a list.**
  The list of a record with a workflow can be viewed as a board with one column per status.
  A list is exported as a CSV file that a spreadsheet can read, with exactly the part the viewer may see.
- **Search records by words.**
  Type a few words in the search box of a list, with no need for accents and no case sensitivity.
  Only the records the viewer may see are shown.
- **An inbox that refreshes by itself.**
  When a record changes, the inbox, the list and the detail page that are open refresh by themselves, with no page reload.
- **Try it at once.**
  One command creates a trial account for each role together with sample records in several states, so that whoever signs in sees a system with work in progress.
- **Simple to install and operate.**
  One command to install, one command to run.
  There is a backup you can verify, services that restart by themselves, virus scanning of attachments, and operational metrics that your own monitoring tool can read.
- **Single sign-on.**
  Through Keycloak, with an optional one-time code for administrators.
- **[Business assistant](../../assistant/README.md) (Vietnamese).**
  An AI model that the team trained, running with the install on your own server.
  It reads the content of a record to suggest how to fill it in, with the words used as evidence, and points out the records that should be looked at again.
  The user confirms before anything is saved.

## Ten ready templates

You can pick them at once in the control panel: student one-stop service, technical support, equipment incident, room booking, IT service access, equipment loan, notes, an asset register, clinic appointments and retail returns.
