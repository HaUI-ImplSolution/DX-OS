# Quick start: bring an approval workflow into your application

Follow this page and you have a working approval workflow: the submitter creates a record, and the approver approves or rejects it.
DX-OS takes care of the rest: data storage, permissions, the workflow and the history.

Pick one of two paths and follow it to the end, from install to use; each path is complete, and you need not read the other.

- [**Path 1, no code**](#path-1-no-code): users work in the web interface that DX-OS already has.
- [**Path 2, with code**](#path-2-with-code): your own application, running in Docker, calls the API of DX-OS.

At every step you work with the **Console** (the web interface `/console`) or the **CLI** (the `dxos` command), whichever you prefer.
The example is an approval workflow for purchase orders with two roles: `REQUESTER` (the submitter) and `APPROVER`.

---

## Path 1. No code

### Step 1. Install and run DX-OS

You need a Linux machine (or Windows with WSL2) with Docker and Docker Compose v2 installed.

```bash
curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
dxos start
```

`dxos start` prints the temporary password of the `admin` account.
If you forget it, run `dxos admin` to get a new temporary password.

Open `http://localhost:9080/console`, sign in as `admin` and change the password.
`admin` is only for administering DX-OS; your application does not use this account.

### Step 2. Create and apply the model

A model is a YAML file that describes which fields a record has, who may do what, and how the approval goes.
Create any working folder (for example `mkdir ~/dxos-model && cd ~/dxos-model`) and save the following as `dxos.yaml` there.
The file lives outside the install folder `~/.dxos`, so updating DX-OS never touches it; keep it in your own git.
With the Console you need not save a file, only paste the content.
If you do not want to type it again, download the sample file: `curl -fsSLO https://raw.githubusercontent.com/HaUI-ImplSolution/DX-OS/main/examples/order_approval/dxos.yaml`.

```yaml
format: 1
name: order_approval
roles:
  - {name: REQUESTER, description: Người nộp đơn}
  - {name: APPROVER, description: Người duyệt đơn, staff: true}
entities:
  order_request:
    label: Đơn đặt hàng
    fields:
      title: {type: text, label: Tiêu đề, required: true}
      amount: {type: number, label: Số tiền, required: true}
    access: {REQUESTER: own, APPROVER: all}
    process: {family: approval, handler: APPROVER}
```

- `roles`: the roles in the workflow.
- `access`: `own` means seeing only your own records, `all` means seeing every record.
- `process`: use the ready-made approval workflow.
  A new record is in the status `submitted`; the `APPROVER` chooses `approve`, `reject` or `return` (send back for more information).
  Rejecting and returning need a reason.

The descriptions and labels in the example are in Vietnamese because they are the text shown to users; write them in your own language.
How to write a model in full is in [Writing a model file](./MODEL_EN.md).
If you do not want to start from nothing, pick a ready template and edit it.

Apply the model:

- **Console:** the **Write and apply** tab, paste the content of the file, click **Show the plan**, then **Apply**. The **Template library** tab has ready-made templates.
- **CLI:** run in the folder that holds `dxos.yaml` (or give the full path of the file).

  ```bash
  dxos lint dxos.yaml    # check the file
  dxos plan dxos.yaml    # see what will change, nothing is changed yet
  dxos apply dxos.yaml   # apply it
  ```

You need to apply only once, with the Console or the CLI, not both.
The result: the **Running models** tab has an **Order approval** card, version 1.

Later, when you edit the file, run `dxos plan` and then `dxos apply` again.
If the plan reports a change of level `MIGRATION` or `LOSS`, add `--accept MIGRATION` or `--accept LOSS` to `dxos apply`.

### Step 3. Create users and assign roles

On the **Users** tab of the Console, click **Create a user** for each real person, and fill in the username and the full name.
The system generates a temporary password and shows it only once: copy it to that person, who must change it at the first sign-in.
Then find the person in the list, click the name and click the role to assign in the **Order approval** section: `REQUESTER` for the submitter, `APPROVER` for the approver.
The role takes effect as soon as that person signs in again.

### Step 4. Use it

The submitter opens `http://localhost:9080/`, signs in with the account they were given, goes to the **Đơn đặt hàng** (purchase orders) menu, clicks **Create**, fills in the title and the amount and clicks **Save**.
The record is in the status **Waiting**.
The approver signs in, sees the record in the **Inbox**, opens it and clicks **Approve**, **Reject** or **Ask for more information**.
The record changes status and the history records who did what, and when.

If you only want to look around and do not need your own model yet: `dxos demo` applies the student one-stop model and creates trial accounts and sample records.
To be notified when a record changes (in a chat or another system), see [webhooks](./INTEGRATIONS_EN.md#event-webhooks).

---

## Path 2. With code

You already have an application that runs from its own image in your own Docker Compose.
You install DX-OS next to it, and then add only a few variables to the application so that it calls DX-OS.
A working example is in [`examples/order_approval`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/examples/order_approval/README.md).
An example with a real sales application, built almost entirely in the Console, is in [`examples/appliance-store`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/examples/appliance-store/README.md).

### Step 1. Install DX-OS next to the application

On the server that runs your application (Linux or WSL2, with Docker Compose v2):

```bash
curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
```

When the install is done, run `dxos start` at once; no configuration file needs editing.
DX-OS opens on `http://localhost:9080` on this machine, and the container of the application calls it through `host.docker.internal` (step 4).
Only when users or the application on another machine need to reach DX-OS, set `PUBLIC_HOST` in `~/.dxos/deploy/.env` to the IP or the name of the server before the first `dxos start`; the list of variables is in [Install and operations](../OPERATIONS.md#biến-cấu-hình-env) (Vietnamese).

```bash
dxos start
```

`dxos start` prints the temporary password of the `admin` account (run `dxos admin` if you forget it), the addresses and what to do next.

### Step 2. Apply the model

Create a folder (for example `mkdir ~/dxos-model && cd ~/dxos-model`) and save the model as `dxos.yaml` there.
You can download the sample file instead of typing it again:

```bash
curl -fsSLO https://raw.githubusercontent.com/HaUI-ImplSolution/DX-OS/main/examples/order_approval/dxos.yaml
```

The content of the file:

```yaml
format: 1
name: order_approval
roles:
  - {name: REQUESTER, description: Người nộp đơn}
  - {name: APPROVER, description: Người duyệt đơn, staff: true}
entities:
  order_request:
    label: Đơn đặt hàng
    fields:
      title: {type: text, label: Tiêu đề, required: true}
      amount: {type: number, label: Số tiền, required: true}
    access: {REQUESTER: own, APPROVER: all}
    process: {family: approval, handler: APPROVER}
```

The model declares three things: the roles (`roles`), who sees which records (`access`, where `own` means your own records and `all` means every record) and the ready-made approval workflow (`process`).
A new record is in the status `submitted`; the `APPROVER` chooses `approve`, `reject` or `return`.
How to write a model in full is in [Writing a model file](./MODEL_EN.md).

Run in that folder:

```bash
dxos plan dxos.yaml    # see what will change, nothing is changed yet
dxos apply dxos.yaml
```

To use the interface: open `http://localhost:9080/console` (from another machine use the address of `PUBLIC_HOST`), sign in as `admin`, go to the **Write and apply** tab, paste the content of the file and click **Apply**.

### Step 3. Connect the application to DX-OS

Each client is a machine account that holds exactly one role.
Run in the folder of the application project, where `docker-compose.yml` is:

```bash
dxos connect order_approval --role REQUESTER --role APPROVER
```

The command registers one client for each role (`dxos-app-order_approval-requester` and `dxos-app-order_approval-approver`) and writes into the `.env` of the current folder: `DXOS_BASE_URL`, then `DXOS_<ROLE>_CLIENT_ID` and `DXOS_<ROLE>_CLIENT_SECRET` for each role.
A newly created `.env` file gets mode `600`, and an existing file is appended to and not overwritten.
On a Windows drive mounted into WSL (`/mnt/c`) this mode cannot be kept and the command prints a warning; put the application folder on the WSL drive, for example under `~`.
Finally the command signs in with the first client, calls the API of the model, and prints the two lines to add to `docker-compose.yml` in step 4.
`order_approval` is the `name` of the model file that was applied and `--role` is the name of a role declared in that file; if you change the `name` or a role name in the model, change it in the command too.
`DXOS_BASE_URL` is `http://host.docker.internal:9080`, the address that a container on the same machine as DX-OS can use; `--base-url` changes it when the application runs on another machine, and a `DXOS_BASE_URL` line that is already in `.env` is kept.

The `secret` is shown only once and the system does not keep it, so the command writes it straight into `.env` without printing it.
Run the command again and a client whose secret is already in `.env` is left as it is.
A client that is already registered but whose secret is no longer in `.env` makes the command stop and tell you to add `--rotate` to issue a new secret (the old secret stops working).
`--app` changes the application name used to name the clients, `--prefix` changes the variable prefix (for example `--prefix DXOS_RETAIL` for a second model) and `--env-file` writes to another file.
This `.env` file holds secrets, so do not put it in git.
In WSL2, a project on a Windows drive has a path like `/mnt/c/...`, while the WSL home folder is somewhere else; run `pwd` and `ls .env` to be sure you are in the right folder before you run the command.

You can also register each client with `dxos client add <name> --model M --role R --env <PREFIX>`, or on the **Outside applications** tab of the Console: click **Register an application**, enter the name, choose the model and the role, and click **Register**.
That way shows the `clientId` (in the form `dxos-app-<name>`) and the secret only once; copy both into `.env` by hand.

### Step 4. Add two lines to the service of the application

In your `docker-compose.yml`, add to the application service:

```yaml
services:
  my-app:
    image: my-app:latest          # your own image, unchanged
    env_file: .env
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

`env_file` puts the variables of `.env` into the container, and `extra_hosts` lets a container on Linux call `host.docker.internal` (Docker Desktop on Windows and macOS already has this name).
The variables in `.env`, all required:

| Variable | Value |
|---|---|
| `DXOS_BASE_URL` | The address of DX-OS that the container can reach: `http://host.docker.internal:9080` |
| `DXOS_<ROLE>_CLIENT_ID` | The `clientId` of the client, one variable for each role the application uses |
| `DXOS_<ROLE>_CLIENT_SECRET` | The `secret` of that client |

The variable names are chosen by your own application; DX-OS does not read them.
An application that uses only one role needs only three variables.

Check two things, one command each:

```bash
# the container reaches DX-OS: it must print 200
docker run --rm --add-host host.docker.internal:host-gateway curlimages/curl -s -o /dev/null -w "%{http_code}\n" http://host.docker.internal:9080/realms/dxos/.well-known/openid-configuration

# the client signs in and can call the API: it must print "works"
dxos client test order_approval-requester
```

`dxos connect` has already checked the first client; `dxos client test` asks for the secret of the client and then goes through three steps (reaching the address, signing in, calling the API of the model) and says which step is broken.
Every error has a fix in [What to look at when it fails](#what-to-look-at-when-it-fails).

### Step 5. Call DX-OS from the code of the application

The application needs to do two things: get a token, then call the API with the token.

**Get a token** with the client of the role you need.
The token lives for about 5 minutes; keep it for repeated use and get a new token when you meet a `401` error.

```bash
curl -fsS \
  --data-urlencode grant_type=client_credentials \
  --data-urlencode "client_id=$DXOS_REQUESTER_CLIENT_ID" \
  --data-urlencode "client_secret=$DXOS_REQUESTER_CLIENT_SECRET" \
  "$DXOS_BASE_URL/realms/dxos/protocol/openid-connect/token"
```

Take `access_token` from the JSON that comes back.

**Create a record** with the token of the `REQUESTER`:

```bash
curl -fsS -X POST "$DXOS_BASE_URL/api/v1/data/order_approval/order_request" \
  -H "Authorization: Bearer $REQUESTER_TOKEN" \
  -H "Idempotency-Key: order-0001" \
  -H "Content-Type: application/json" \
  -d '{"title": "Mua 10 màn hình", "amount": 50000000}'
```

The result has `data.id` as the record id, in the status `submitted`.
`Idempotency-Key` is a key you set for each business request; sending the same key again does not create a second record.

**Approve** with the token of the `APPROVER`:

```bash
curl -fsS -X POST "$DXOS_BASE_URL/api/v1/data/order_approval/order_request/<id>/actions/approve" \
  -H "Authorization: Bearer $APPROVER_TOKEN" \
  -H "Content-Type: application/json" -d '{}'
```

To reject or return, call `reject` or `return` with a reason: `-d '{"note": "Thiếu báo giá"}'`.
The status changes only through `actions`; do not edit the `status` field directly.

**Read a record:** `GET $DXOS_BASE_URL/api/v1/data/order_approval/order_request/<id>`, and `.../<id>/events` for the history.

The same steps in Python:

```python
import os, requests

BASE = os.environ["DXOS_BASE_URL"]
URL = f"{BASE}/api/v1/data/order_approval/order_request"

def auth(role):  # role: "REQUESTER" or "APPROVER"
    r = requests.post(f"{BASE}/realms/dxos/protocol/openid-connect/token", data={
        "grant_type": "client_credentials",
        "client_id": os.environ[f"DXOS_{role}_CLIENT_ID"],
        "client_secret": os.environ[f"DXOS_{role}_CLIENT_SECRET"],
    })
    r.raise_for_status()
    return {"Authorization": f"Bearer {r.json()['access_token']}"}

created = requests.post(URL, headers={**auth("REQUESTER"), "Idempotency-Key": "order-0001"},
                        json={"title": "Mua 10 màn hình", "amount": 50000000})
created.raise_for_status()
order_id = created.json()["data"]["id"]

requests.post(f"{URL}/{order_id}/actions/approve", headers=auth("APPROVER"), json={}).raise_for_status()
```

A version that runs, using only standard Python and packed into Docker Compose, is in [`examples/order_approval`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/examples/order_approval/README.md).
The list of fields and actions of a model is in its own OpenAPI: `GET /api/v1/meta/models/order_approval/openapi`.
Use that document to generate a client instead of writing one by hand.

### Step 6. Know when a record changes (optional)

- **Ask again:** call `GET` on the record or on the list, for example `?filter.status=approved`.
  The simplest way.
- **Webhook:** DX-OS calls the address of the application every time a record changes, with a signature to verify.
  How to declare it is in [Integrating with outside systems](./INTEGRATIONS_EN.md#event-webhooks); an application on the same machine uses the address `http://host.docker.internal:<port>/...` in `deploy/webhooks/targets.yml`.

---

## What to look at when it fails

| Symptom | Common cause | Fix |
|---|---|---|
| The container cannot reach DX-OS (`curl` prints `000`, "connection refused") | The service lacks `extra_hosts`, or `DXOS_BASE_URL` uses `localhost`, or the application runs on another machine | Add `extra_hosts` as in step 4 and use `DXOS_BASE_URL=http://host.docker.internal:9080`; for an application on another machine, set `PUBLIC_HOST` to the IP or the name of the server, run `dxos start`, then change `DXOS_BASE_URL` in `.env` to `http://<PUBLIC_HOST>:9080` |
| Sign-in says `401` or "Invalid client credentials" | A wrong `clientId` (it must be `dxos-app-<name>`, not `<name>`) or a wrong or lost secret | Check with `dxos client test <name>`; if the secret is lost, run `dxos connect` again with `--rotate`, or `dxos client rotate <name> --env <PREFIX>` |
| An API call says `400` | A required field is missing, or `reject` and `return` have no `note` | Read the error content: it names the missing field or reason; send it again in full |
| An API call says `401` | The token has expired (it lives about 5 minutes) | Get a new token and call again |
| An API call says `403` | The client of this role may not do that (for example `REQUESTER` calling `approve`), or it calls another model | Use the client of the right role; do not retry the same call |
| An API call says `409` | The record is not in a status that allows this action, or the `Idempotency-Key` was used for different content | Read the record again and decide; use a new key for a new request |
| `dxos apply` is refused | The plan has a change heavier than the safe level, or the file is wrong | Read the result of `dxos plan`; to accept, add `--accept MIGRATION` or `--accept LOSS`; for a wrong file, `dxos lint` points at the exact line and column |
| `/console` returns 404 | DX-OS is running in core-only mode | `dxos start --full` |
| You forgot the `admin` password | | `dxos admin` prints a new temporary password |
| `dxos start` was interrupted, or says a port is taken | Only some of the services are running, or another program holds the port `APISIX_HOST_PORT` or `POSTGRES_HOST_PORT` | Run `dxos start` again, which builds what is missing; for a port held by another program, `dxos doctor` names the key to change in `deploy/.env` |
| It is not clear which service is broken | | `dxos status`, then `dxos logs <service>` |

If the cause is still unclear, see [incident handling](../OPERATIONS.md#ứng-phó-sự-cố) (Vietnamese).

## More commands worth knowing

| Command | Use |
|---|---|
| `dxos status` | See which service is running and the addresses |
| `dxos logs [service]` | Read the log when something fails |
| `dxos doctor` | Check the machine and the configuration before you run |
| `dxos client list` | See the registered clients, with the model and the role |
| `dxos client test <name>` | Check that a client signs in and can call the API |
| `dxos client rotate <name>` | Issue a new secret when it is lost or leaked; the old secret stops working at once |
| `dxos client remove <name>` | Delete a client that is no longer used |
| `dxos backup` | Encrypted backup of the data and the files |
| `dxos update` | Update to a new version, keeping the configuration and the data |
| `dxos stop` | Stop the system, keeping the data |

If you need only the API, without the web interface and the Console: `dxos start --core`.
Then use the CLI for every operation.

All the commands are in [Install and operations](../OPERATIONS.md#cài-đặt-và-lệnh-dxos) (Vietnamese).

## Read next

- [Writing a model file](./MODEL_EN.md): write a model of your own.
- [Extension points](./EXTENSIONS_EN.md#a-separate-service-that-calls-the-api): testing, packaging and upgrading an application that calls the API; a web interface on another domain.
- [API](./API_EN.md): the paths of the API.
- [Integrating with outside systems](./INTEGRATIONS_EN.md): webhooks, pulling events, tamper-evident history.
- [Install and operations](../OPERATIONS.md) (Vietnamese): the `.env` configuration variables, backup, restore, incident handling.
