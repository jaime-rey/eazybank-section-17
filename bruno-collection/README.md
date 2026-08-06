# EazyBank s17 — Bruno collection

Colección de pruebas para el cluster local. Contra `dev-env` (gateway en `localhost:8072`)
y Keycloak en `localhost:80`.

## Requisitos previos

1. Cluster levantado: `.\deploy-cluster.ps1` desde la raíz del proyecto.
2. Cliente OAuth `eazybank-callcenter-cc` creado en Keycloak con roles `ACCOUNTS,CARDS,LOANS`:
   ```powershell
   .\create-keycloak-client.ps1 -ClientId eazybank-callcenter-cc -Roles ACCOUNTS,CARDS,LOANS
   ```
   El script imprime el `secret` — cópialo.
3. Bruno instalado: https://www.usebruno.com/

## Configurar

1. Abre Bruno → **Open Collection** → selecciona esta carpeta `bruno-collection`.
2. Panel de environments (arriba a la derecha) → selecciona **Local**.
3. Edita el env `Local` (icono lápiz) y pega el `clientSecret` que devolvió el script.
   El `token` se rellena solo cuando corras `Auth/Get Token`.

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
