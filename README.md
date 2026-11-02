# projektZTiD — Secure File Sharing

**ZTiD project | Politechnika Śląska | Team: Ryczan, Różycki, Sarwa, Czuchra**

## Moduły bezpieczeństwa
| Mechanizm | Autor | Gałąź |
|-----------|-------|-------|
| JWT | Ryczan | Ryczan |
| **TOTP (RFC 6238)** | **Różycki** | **Rozycki** |
| OAuth 2.0 / GitHub | Sarwa | Sarwa |
| RBAC | Czuchra | Czuchra |

---

## Uruchomienie

### Backend (Spring Boot)
```bash
cd backend

# Linux/Mac
JAVA_HOME=/path/to/jdk17 ./mvnw spring-boot:run

# Windows (PowerShell)
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
& "C:\Users\<user>\.m2\wrapper\dists\apache-maven-3.9.15\<hash>\bin\mvn.cmd" spring-boot:run
```
API dostępne na `http://localhost:8080`  
H2 console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:ztiddb`)

### Frontend (React + Vite)
```bash
cd frontend
npm install
npm run dev
```
Aplikacja na `http://localhost:5173` (proxy `/api` → `:8080`)

---

## Endpointy API

| Metoda | Ścieżka | Auth | Opis |
|--------|---------|------|------|
| POST | `/api/auth/register` | — | Rejestracja |
| POST | `/api/auth/login` | — | Logowanie; zwraca `totpRequired` |
| POST | `/api/auth/logout` | sesja | Wylogowanie |
| GET | `/api/auth/me` | sesja | Sprawdzenie sesji |
| GET | `/api/totp/setup` | sesja | Generuje sekret + `otpauth://` URI |
| POST | `/api/totp/enable` | sesja | Aktywuje TOTP po weryfikacji kodu |
| POST | `/api/totp/verify` | TOTP_PENDING | Weryfikacja kodu przy logowaniu |
| POST | `/api/totp/disable` | sesja | Dezaktywuje TOTP |
| GET | `/api/totp/status` | sesja | Sprawdza czy TOTP włączone |
| GET | `/api/files` | sesja | Lista plików (własne + udostępnione) |
| POST | `/api/files/upload` | sesja | Upload pliku (multipart/form-data) |
| GET | `/api/files/{id}/download` | sesja | Download pliku |
| POST | `/api/files/{id}/share` | sesja | Udostępnienie użytkownikowi |

---

## TOTP — przepływ logowania

```
POST /api/auth/login  { username, password }
         │
         ├─ password wrong ──► 401 Unauthorized
         │
         ├─ totpEnabled=false ──► sesja pełna ► 200 { authenticated: true }
         │
         └─ totpEnabled=true ──► sesja TOTP_PENDING ► 200 { totpRequired: true }
                  │
                  ▼
         POST /api/totp/verify  { code }
                  │
                  ├─ kod błędny ──► 401
                  └─ kod OK ──► sesja pełna ► 200 { authenticated: true }
```
