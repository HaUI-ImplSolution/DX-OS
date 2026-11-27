---
slug: /intro
---

# Introduction

This site holds the documentation of [DX-OS](https://github.com/HaUI-ImplSolution/DX-OS): a dynamic core for record-and-workflow systems.
The core takes a model file that declares entities, fields, roles, permissions and workflows, and builds the data tables, the sign-in roles, an API with row-level permissions, the approval workflow and the interface.

Part of the documentation is available in English and part only in Vietnamese; pages without an English version open in Vietnamese, and the table below marks them.

## Where the documentation is {#tài-liệu-nằm-ở-đâu}

| Content | Where to look |
|---|---|
| Using the system by role: submit, handle, deploy | [User guides](/docs/huong-dan/nguoi-nop) in the sidebar of this site |
| Introduction, install, the table of contents of the documents | [README](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/README_EN.md) in the repository |
| The three tiers and how services call each other | [Architecture](/docs/tai-lieu/kien-truc) |
| Backend modules, build, configuration | [backend/README.md](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/backend/README.md) (Vietnamese) |
| Install, the `dxos` commands, backup, virus scanning | [Install and operations](/docs/tai-lieu/van-hanh) (Vietnamese) |
| The SeaweedFS attachment store | [Attachment storage](/docs/tai-lieu/luu-tep) |
| Concurrent users and the smallest configuration | [Sizing and concurrent load](/docs/tai-lieu/cau-hinh-va-tai) |
| Logs, rate limits, sign-in events, audit, monitoring | [Monitoring and logs](/docs/tai-lieu/giam-sat) |
| Event webhooks, tamper-evident history, the data lake | [Integrating with outside systems](/docs/tai-lieu/tich-hop) |
| Connecting a reporting tool through `report_ro` | [Connecting a reporting tool](/docs/tai-lieu/bao-cao) |
| Building services, webhooks and custom interfaces | [Extension points](/docs/tai-lieu/mo-rong) |
| API reference | The OpenAPI specification `/v3/api-docs` of the running backend; the table of paths in the [API](/docs/tai-lieu/api) page |
| Changes by version | [CHANGELOG.md](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/CHANGELOG.md) (Vietnamese) and [Releases](https://github.com/HaUI-ImplSolution/DX-OS/releases) |
| What DX-OS can do, in plain words | [Features](/docs/tai-lieu/tinh-nang) |
| Writing a model file | [Writing a model file](/docs/tai-lieu/viet-mo-hinh) |
| Hard words and terms | [Glossary](/docs/tai-lieu/thuat-ngu) |
| Testing guide | [docs/TESTING.md](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/TESTING.md) (Vietnamese) |
| Contributing, security, code of conduct | [CONTRIBUTING.md](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/CONTRIBUTING.md), [SECURITY.md](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/SECURITY.md) (Vietnamese) |
| Evaluation and the contest dossier | The sections in the sidebar of this site (Vietnamese) |

## Sections on this site {#các-mục-trên-trang-này}

- **Images and videos:** screenshots of the product and two short videos.
- **Product documentation:** features, writing a model file, the API, install and operations, attachment storage, sizing and concurrent load, monitoring, integrations, reporting, extension points, architecture, compatibility between versions, the demo script and the glossary; these pages are copied from `docs/` and `deploy/` each time the site is built, so there is no second copy to edit.
- **User guides:** three pages by role: the person who submits records, the person who handles them and the person who deploys the system.
- **Evaluation:** the evidence matrix for each feature and the PoF audit against the contest brief.
- **Contest:** the questions the jury may ask, and the components chosen and not chosen.
- **Phase document templates:** templates for opening a new phase of work.
