# Guide for the person who handles records

This page is for officers, approvers, technicians and managers: everyone who holds a handling role in a business.

## Inbox {#hộp-việc}

The **Inbox** lists the records waiting for you, with overdue work first.
The **Waiting** count updates by itself within a few seconds when a new record arrives, with no page reload.
You see only the records within the scope of your role: every record, the records of your unit, the records assigned to you, or read-only.

## Handle a record {#xử-lý-một-hồ-sơ}

Open the record from the inbox.
The action buttons shown depend on the current status and on your role.
Each business picks a workflow family to start from:

| Family | Actions of the handler |
|---|---|
| Approval | **Approve**, **Reject**, **Ask for more information** |
| Work item | **Start handling**, **Mark as resolved**, **Reject** |
| Booking | **Confirm**, **Reject**, **Start using**, **Complete**, **No-show** |

Rejecting and asking for more information both need a reason; the submitter reads that reason in the history.
A business can add or change actions, and some actions show only when the record meets a condition, for example an amount over a limit must go up to a higher level of approval.

## Print documents {#in-giấy-tờ}

If the business has print templates, the record detail page shows a **Print** button in the statuses the template allows; for example a certificate can be printed only once the record is approved.
Click the button to download the PDF of the record.

## Find records {#tìm-hồ-sơ}

The **Search by words** box at the top of the list takes one or a few words; a record shows up when it contains all of them in its text fields, its code or its choices.
You need not type accents and case does not matter, so `don nghi` finds "Đơn xin nghỉ phép".
If the business assigns record codes, type the whole code or part of it in the same box.
The code is at the top of the detail page with a copy button, handy for pasting into a conversation or minutes.

## Take and assign work {#nhận-và-giao-việc}

A record that is not assigned can be handled by anyone with the handling role.
**Take it** or **Assigned** (pick a person in the **Assign to** box) makes only the assigned person able to handle it; **Unassign** returns the record to the whole role.

## Deadline {#hạn-xử-lý}

When the business has a deadline, an overdue record shows the **Overdue** label and goes to the top of the inbox.
When the business has a working calendar, the deadline counts only working hours and skips days off, so a record that arrives on Friday afternoon is not overdue over the weekend.
The history records when a record is about to reach its deadline and when it becomes overdue.
When the business declares escalation, the history also records each time an overdue record is reported up to another role; this does not change who holds the record.

## Handle many records at once {#xử-lý-nhiều-hồ-sơ-cùng-lúc}

Select several records in the list and click an action; up to 50 records at a time.
Each record still goes through all of its own rules: a record that cannot be handled is reported with a reason, and the others still run.
There is no undo button; a record handled by mistake is handled further along the workflow, for example reopened or returned if the workflow has that.

## Business assistant {#trợ-lý-nghiệp-vụ}

The detail page of the kinds of record that declare a task shows the **Business assistant** block.
The first time, open **Business assistant** in the menu and click **Link DX-OS**.
Choose a task and click **Create suggestion**: the assistant gives a suggested value with the evidence and the confidence, or says there is not enough evidence.
The value is saved only when you confirm.

In the Inbox, the **Review record classifications** button reviews up to 25 records per round.
A record whose value differs from the suggestion is highlighted, marked **Needs review** and moved to the top of the page.
Click the **Needs review** mark to open the record and see the suggestion.
The marks last about two minutes; records that could not be read are counted and can be reviewed again.
A suggestion can be wrong, so check the content of the record before you confirm.

## Other ways to look {#các-cách-xem-khác}

- **By status:** the list shows as a board, one column per status.
- **Statistics:** the number of records by status and unit, the average handling time, and the share closed on time when the business has a deadline; Report by field groups records by a field or by month and counts them, or adds up or averages a numeric field.
- **Export CSV:** exports the list to a file a spreadsheet can read, with exactly the part you may see.
- **Calendar:** for booking businesses, the bookings by day and by resource.

## Rules that cannot be bypassed {#quy-tắc-không-lách-được}

- The person who created a record does not handle their own record.
- A record that has been decided cannot be edited.
- A record assigned to someone else can be handled only by that person.
- The status changes only through the workflow buttons; calling the API directly to change the status is blocked.
- Each handling step leaves a row in the history, and the history is chained so that anyone who edits, deletes or inserts a row is pointed out.
