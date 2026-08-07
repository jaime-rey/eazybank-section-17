# EazyBank s17 — Bruno collection

Colección de pruebas contra el gateway. Dos entornos:

- **Local**: cluster de Docker Desktop (gateway `localhost:8072`, Keycloak `localhost:80`).
- **Remote-GKE**: cluster de GKE con LoadBalancer public IPs (ver `HANDOFF.md` §"Deploy en GKE").

## Requisitos previos

1. Cluster levantado. Para local: `.\deploy-cluster.ps1`. Para GKE: seguir la receta
   del `HANDOFF.md` (arrancar cluster + `kubectl apply` de infra + `helm install`).
2. Cliente OAuth `eazybank-callcenter-cc` creado en el Keycloak del cluster que vayas
   a usar (Keycloak corre H2 en memoria — al recrear el cluster hay que rehacerlo):
   ```powershell
   .\create-keycloak-client.ps1 -ClientId eazybank-callcenter-cc -Roles ACCOUNTS,CARDS,LOANS
   ```
   El script hace `kubectl exec` al pod de keycloak, así que respeta el contexto de
   `kubectl` — funciona igual contra Docker Desktop o GKE. Imprime el `secret`.
3. Bruno instalado: https://www.usebruno.com/

## Configurar

1. Abre Bruno → **Open Collection** → selecciona esta carpeta `bruno-collection`.
2. Panel de environments (arriba a la derecha) → **Local** o **Remote-GKE**.
3. Edita el env (icono lápiz) y **pega el `clientSecret` que devolvió el script**
   (los ficheros traen `PASTE_HERE_FROM_create-keycloak-client.ps1` como placeholder —
   no se commitea el valor real). El `token` se rellena solo cuando corras
   `Auth/Get Token`.
4. Para **Remote-GKE**, verifica que las IPs del env coinciden con las actuales:
   ```powershell
   kubectl get svc gatewayserver keycloak
   ```
   Si cambian (por recrear el cluster), actualiza `gatewayUrl` y `keycloakUrl` en el env.
   Los puertos son: gateway `:8072`, keycloak `:80`.

## Cómo usar

### Uso normal (una request a la vez)

1. Corre **Auth → Get Token** una vez. Guarda `{{token}}` en el env.
2. Corre cualquier request de Accounts/Cards/Loans. Usa `{{mobileNumber}}` del env
   (por defecto `5551000001`). Cambia `mobileNumber` en el env para probar otros.

### Uso data-driven (5 clientes desde `data/customers.json`)

Bruno tiene un **Runner** iterativo:

1. Corre **Auth → Get Token** una vez (llena `{{token}}`).
2. Click derecho sobre carpeta `Accounts` → **Run Folder**.
3. En el diálogo del Runner, en el campo **Data**, selecciona `bruno-collection/data/customers.json`.
4. Marca "Iterate over data file". Corre.
5. Bruno ejecuta Create → Fetch → Update → Delete para cada uno de los 5 clientes.
   En cada iteración `{{name}}`, `{{email}}`, `{{mobileNumber}}` toman los valores de la fila.

Después del Runner de `Accounts`, si quieres probar Cards/Loans **sin** el Delete final,
comenta la línea `seq: 4` en `Accounts/Delete.bru` o simplemente sáltalo antes de correr
los otros folders.

## Flujo end-to-end recomendado

```
Auth/Get Token
    └── Accounts (Runner con customers.json, quitando Delete)
        └── Cards (Runner con customers.json)
            └── Loans (Runner con customers.json)
                └── Accounts/Delete (uno a uno para limpiar — borra tambien cards/loans en cascada)
```

## Renovar el token

El token dura ~1 minuto (config por defecto de Keycloak master). Cuando veas 401 en
requests que sí llevan `Authorization`, vuelve a correr **Auth → Get Token**.

## Renovar el secret

Si Keycloak reinicia (H2 en memoria), el cliente se pierde. Vuelve a lanzar
`create-keycloak-client.ps1` y actualiza `clientSecret` en el env.

## Endpoints sin auth vs con auth

- **GET** (`Fetch`, `contact-info`, etc.) → `permitAll`, no requieren token.
- **POST / PUT / DELETE** → requieren `Bearer` con roles `ACCOUNTS`/`CARDS`/`LOANS`
  según el path.
