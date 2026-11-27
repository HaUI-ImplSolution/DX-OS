# Guide for the person who submits records

This page is for people who send requests: a student filing an application, an employee asking for access, a user reporting an incident or booking a room.
The names of the menu items and the fields of the forms are set by the business where you work; the way of working is the same in every business.

## Sign in {#đăng-nhập}

Sign in once with the account the deployer gave you; the first time, the system makes you change the temporary password.
The VI and EN buttons in the top corner switch the language of the interface.
You see only your own records.

## Create a record {#tạo-hồ-sơ}

1. Open the business in the menu and click **Create**.
2. Fill in the form; required fields are marked, and a wrong value (a number outside its limits, a string in the wrong pattern, a choice that does not exist) is refused at once with a hint.
3. If there is a **Đơn vị xử lý** (handling unit) box, choose the office that will handle the record.
4. Save.

When your inbox is empty, that page has a **Create** button for each kind of record you may create; click one to open its form.

A box that picks another record (for example a product or a room) lets you type part of the name to narrow the list, including records that are not among the items shown; type the Vietnamese accents correctly, and when no record matches, the box says there is no result.

The record enters the first status of the workflow, usually **Waiting**, and the handler sees it in their inbox.

## Attach files {#đính-kèm-tệp}

Open the record and add files in the attachments section.
Some kinds of file are refused, for example HTML files and RAR files (including a RAR file renamed to another extension).
Only the person who added a file can remove it.

## Print documents {#in-giấy-tờ}

A business can declare print templates, for example a certificate.
When the record is in a status the template allows, the detail page shows a **Print** button (or **Print: template name** when there is only one template), and a click downloads the PDF of the record.
Only people who can see the record can print it.

## Record code {#mã-hồ-sơ}

A business can give each record a short code, for example `HS-2026-00007`.
The code shows in the first column of the list and at the top of the detail page, with a copy button; use it when you ask about a record or look it up with the handler.
You do not type the code: the system assigns it when the record is created and it never changes.
Type part of the code in the **Search by words** box of the list and only the records with that code remain.

## Follow a record {#theo-dõi-hồ-sơ}

- The status of the record shows in the list and at the top of the detail page.
- **History** on the detail page tells who did what and when, with the reason when the handler rejects or returns the record and the file name when someone adds or removes a file.
  The history cannot be edited.
- An open page refreshes itself when the record changes, so you do not need to reload.
- If the record has a handling deadline, the deadline shows on the detail page.

## When a record is returned {#khi-hồ-sơ-bị-trả-lại}

The status **Needs more information** means the handler needs more information; their reason is in the history.
Edit the record and click **Submit again**.
A record that is **Rejected** or **Approved** is final and cannot be edited.

## Confirm the result {#xác-nhận-kết-quả}

In a work-item business, when the handler clicks **Mark as resolved** you choose:

- **Confirm it is done** to close the record;
- **Reopen** with a reason if the result is not good enough.

If the business declares a waiting time and you do nothing, the system closes the record when that time runs out, and the history says so.

## Withdraw {#rút-lại}

Click **Withdraw** when the record no longer needs handling; the button shows only in the statuses the business allows.
A withdrawn record stops being handled and cannot be submitted again.
A record with a workflow cannot be deleted, so withdrawing is the way to stop it.

## Booking {#đặt-chỗ}

In a room or equipment booking business, choose the resource and the time slot.
A slot that goes over the capacity is refused at once.
A booking waits for the manager to **Confirm** it; the calendar page shows the bookings by day.

## What you cannot do {#những-việc-bạn-không-làm-được}

- Approve or reject a record you created yourself.
- See other people's records.
- Change the status of a record other than with the workflow buttons offered.
