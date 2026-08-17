# EazyBank s17 — Bruno collection

Test collection against the gateway. Two environments:

- **Local**: Docker Desktop cluster (gateway `localhost:8072`, Keycloak `localhost:80`).
- **Remote-GKE**: GKE cluster with LoadBalancer public IPs (see `HANDOFF.md` §"Deploy on GKE").

## Prerequisites

1. Cluster up. For local: `.\deploy-cluster.ps1`. For GKE: follow the recipe in
   `HANDOFF.md` (bring up cluster + `kubectl apply` for infra + `helm install`).
2. OAuth client `eazybank-callcenter-cc` created in the Keycloak of the cluster you
   are going to use (Keycloak runs H2 in memory — recreating the cluster means
   you have to recreate the client):
   ```powershell
   .\create-keycloak-client.ps1 -ClientId eazybank-callcenter-cc -Roles ACCOUNTS,CARDS,LOANS
   ```
   The script does a `kubectl exec` into the keycloak pod, so it respects the
   current `kubectl` context — it works the same against Docker Desktop or GKE.
   It prints the `secret`.
3. Bruno installed: https://www.usebruno.com/

## Configure

1. Open Bruno → **Open Collection** → select this `bruno-collection` folder.
2. Environments panel (top right) → **Local** or **Remote-GKE**.
3. Edit the env (pencil icon) and **paste the `clientSecret` returned by the script**
   (the files ship `PASTE_HERE_FROM_create-keycloak-client.ps1` as a placeholder —
   the real value is not committed). The `token` fills itself in when you run
   `Auth/Get Token`.
4. For **Remote-GKE**, verify that the IPs in the env match the current ones:
   ```powershell
   kubectl get svc gatewayserver keycloak
   ```
   If they change (because you recreated the cluster), update `gatewayUrl` and
   `keycloakUrl` in the env. Ports are: gateway `:8072`, keycloak `:80`.

## How to use

### Normal use (one request at a time)

1. Run **Auth → Get Token** once. Stores `{{token}}` in the env.
2. Run any Accounts/Cards/Loans request. Uses `{{mobileNumber}}` from the env
   (default `5551000001`). Change `mobileNumber` in the env to try others.

### Data-driven use (5 customers from `data/customers.json`)

Bruno has an iterative **Runner**:

1. Run **Auth → Get Token** once (fills `{{token}}`).
2. Right-click the `Accounts` folder → **Run Folder**.
3. In the Runner dialog, in the **Data** field, select `bruno-collection/data/customers.json`.
4. Check "Iterate over data file". Run.
5. Bruno executes Create → Fetch → Update → Delete for each of the 5 customers.
   On each iteration `{{name}}`, `{{email}}`, `{{mobileNumber}}` take the values
   from the row.

After the `Accounts` Runner, if you want to try Cards/Loans **without** the final
Delete, comment out the `seq: 4` line in `Accounts/Delete.bru` or simply skip it
before running the other folders.

## Recommended end-to-end flow

```
Auth/Get Token
    └── Accounts (Runner with customers.json, skipping Delete)
        └── Cards (Runner with customers.json)
            └── Loans (Runner with customers.json)
                └── Accounts/Delete (one by one to clean up — also cascades cards/loans)
```

## Renew the token

The token lasts ~1 minute (default Keycloak master config). When you see 401 on
requests that do carry `Authorization`, re-run **Auth → Get Token**.

## Renew the secret

If Keycloak restarts (H2 in memory), the client is lost. Run
`create-keycloak-client.ps1` again and update `clientSecret` in the env.

## Endpoints without auth vs with auth

- **GET** (`Fetch`, `contact-info`, etc.) → `permitAll`, no token required.
- **POST / PUT / DELETE** → require `Bearer` with roles `ACCOUNTS`/`CARDS`/`LOANS`
  depending on the path.
