# Guide for the person who deploys

This page is for the person who installs DX-OS on a server, picks or writes the model file of a business, gives out accounts and runs the system.
Each command and option is in [`docs/OPERATIONS.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/OPERATIONS.md) (Vietnamese); this page only shows the way.
To go from a clean machine to a working process, or to connect DX-OS to your own application, follow the [Quick start](/docs/tai-lieu/bat-dau-nhanh).

## Install and run {#cài-và-chạy}

```bash
curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
dxos start
```

- `dxos start` starts the services and waits until they are ready; with one or more model files it applies them at startup.
- `dxos status` shows the state of each service and the addresses; `dxos doctor` checks the machine before you run.
- `dxos admin` sets a temporary password for the `admin` account; it must be changed at the first sign-in.
- `dxos demo` creates four trial accounts and sample records so you can see the system with work in progress.

The main addresses: `/` is the interface of the users, `/console` is your control panel, and `/portal/` lists the addresses and the state of the services.

## Choose a business {#chọn-nghiệp-vụ}

Open `/console` with an `ADMIN` account.

- **Template library:** pick one of the ready templates (student one-stop service, technical support, room booking, equipment loan and others) and apply it.
- **Write and apply:** write a model file, or build it with the form: the tree on the left shows the whole model, clicking a status or an arrow on the workflow diagram goes to the place to edit it, and an error points at exactly the section to fix.
  Each part has a guide that says what you can write.
  Opening another template or model while the draft has changes that are not applied makes the control panel ask first; choose Cancel to keep the draft.

Adding a new business means adding a model file; how to write it is in [Writing a model file](/docs/tai-lieu/viet-mo-hinh).

## Change a business that already has data {#đổi-nghiệp-vụ-khi-đã-có-dữ-liệu}

1. Open the running version on the **Write and apply** tab and edit it.
2. Click **Show the plan**: the plan states the safety level, which changes can carry the data over, and which changes are refused and why.
   An error in the file is pointed at by line and column.
   When an action of the workflow changes, the plan says which action changes what; if that action now requires one more field, the plan says how many waiting records still lack that field and will be refused until it is filled in.
3. Click **Apply** when the plan is what you want; the running system is not stopped.
4. To go back, on the **Running models** tab click **History** of the model and then **Go back to the previous version**; going back also has a plan to preview.

A refused plan changes nothing; the system keeps running the old version.
From the command line use `dxos plan <file>` and `dxos apply <file>`.
To keep the model files in git and have them compared with the running system automatically, use `dxos gitops`.

## Users and roles {#người-dùng-và-vai}

The **Users** tab of `/console` lets you find users, create users, assign and remove roles and units, lock and unlock accounts, and reset passwords.
A temporary password is shown once and the user must change it at the first sign-in.
The platform role `ADMIN` is not granted on this tab but in the Keycloak Admin Console.

## Connect other systems {#nối-hệ-thống-khác}

- **Webhooks:** every change of a record is reported to a web address or a chat channel, signed so the receiver can verify it; details are in [Integrating with outside systems](/docs/tai-lieu/tich-hop).
- **Outside applications:** the **Outside applications** tab, `dxos client add` or `dxos connect` (one command for every role, which also writes the `.env` of the application) registers an application that calls the API with exactly one role of one model.
- **Projects:** the **Project** tab or `dxos project add` groups the models and outside applications of one integrated system so you can filter and see the reach of a change; a project grants no permission and splits no data, and a project administrator (`dxos project member add --admin`) assigns the roles of the models in the project to their own members; details are in the Projects section of [`docs/OPERATIONS.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/OPERATIONS.md#dự-án) (Vietnamese).
- **Event queue:** the tab of the same name shows the work waiting to be sent and retries it on error.
- **Reporting tools:** connect through the read-only role `report_ro`; see [Connecting a reporting tool](/docs/tai-lieu/bao-cao).

## Data and safety {#dữ-liệu-và-an-toàn}

- **Data catalog:** the tab of the same name shows where the data of each model lives, who may see it, and where it comes from and goes to, down to each field.
- **Backup:** `dxos backup` takes an encrypted copy of the database and the files; keep the backup password somewhere other than the server.
- **Tamper-evident history:** `dxos history verify` checks that the history chain of the records has not been edited.
- **Long-term archive:** `dxos archive` writes closed records to files for storage and checking.
- **Virus scanning of uploads:** on by default; `dxos av off` turns it off, `dxos av` shows the state.
- **Checking model files:** `dxos lint <file>` reports errors by line and column without a running system; `dxos conformance <model>` checks the guard rails of the core on a running stack.
- **Update:** `dxos update` installs a new version and keeps the configuration and the data.
- **Pull from git:** `dxos gitops` pulls the infrastructure from a DX-OS repository and the model files from a repository of yours, then reports drift, waits for your approval or applies on its own; how to configure it is in the Pull from git section of `docs/OPERATIONS.md` (Vietnamese).
