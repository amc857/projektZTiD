# Projekt ZTiD

Aplikacja webowa do bezpiecznego udostępniania i zarządzania plikami z uwierzytelnianiem dwuskładnikowym (TOTP), obsługą JWT, logowaniem przez GitHub OAuth2 oraz panelem administracyjnym. Składa się z backendu w **Spring Boot** oraz frontendu w **React (Vite + Tailwind CSS v4)**.

---

## Główne funkcjonalności

- **Uwierzytelnianie i autoryzacja:** Rejestracja i logowanie (JWT + sesja), integracja z **GitHub OAuth2**, kontrola dostępu oparta na rolach (`ROLE_USER`, `ROLE_ADMIN`).
- **Uwierzytelnianie dwuskładnikowe (2FA / TOTP):** Konfiguracja kodów jednorazowych (np. Google Authenticator) oraz weryfikacja przy logowaniu.
- **Zarządzanie plikami:** Przesyłanie, pobieranie, udostępnianie plików innym użytkownikom (z walidacją istnienia użytkownika) oraz usuwanie własnych zasobów.
- **Ustawienia konta (`/account`):** Zmiana nazwy użytkownika (z automatycznym odświeżeniem tokena JWT), zmiana hasła oraz zarządzanie 2FA.
- **Panel administratora:**
  - Wyszukiwarka użytkowników w bazie zwracająca maksymalnie.
  - Edycja konta wybranego użytkownika (login, reset hasła, zmiana roli `ROLE_USER` / `ROLE_ADMIN`, wymuszenie wyłączenia 2FA).
  - Zarządzanie plikami wybranego użytkownika (zmiana nazwy pliku, usuwanie plików z bazy).

---

## Wymagania technologiczne

- **Backend:** Java 25 (JDK 25), Spring Boot 3.5.x, Spring Security, Maven, baza danych H2
- **Frontend:** Node.js 20+, React 18, Vite, Tailwind CSS v4, Axios, Lucide Icons
- **Konteneryzacja:** Podman (z `podman-compose`) lub Docker

---

## Uruchomienie projektu (Podman / Kontenery) — Zalecane

Projekt jest w pełni skonfigurowany do działania w kontenerach, co nie wymaga lokalnej instalacji Javy, Mavena ani Node.js.

### 1. Wymagania wstępne
Upewnij się, że masz zainstalowanego **Podmana** (np. poprzez *Podman Desktop*) i włączoną maszynę wirtualną Podmana (na systemie Windows/macOS):
```powershell
podman machine start
```

### 2. Budowanie i uruchomienie kontenerów
W głównym katalogu projektu (tam, gdzie znajdują się pliki Dockerfile oraz docker-compose.yml) uruchom:
```powershell
podman compose up --build
```

### 3. Domyślne konto administratora
Przy pierwszym uruchomieniu aplikacji automatycznie (DataInitializer) tworzone jest konto administratora, jeśli nie istnieje jeszcze w bazie:

    Login: admin

    Hasło: admin

    Rola: ROLE_ADMIN

(Zaleca się zmianę hasła po pierwszym zalogowaniu w zakładce Ustawienia konta).

### 4. Dostęp do aplikacji

Po zbudowaniu i uruchomieniu kontenerów poszczególne elementy są dostępne pod adresami:

  - Frontend (Aplikacja React): http://localhost:5173

  - Backend API: http://localhost:8080

  - Konsola bazy danych H2: http://localhost:8080/h2-console

      - JDBC URL: jdbc:h2:mem:ztiddb (lub jdbc:h2:file:/app/data/ztiddb w przypadku bazy plikowej)

      - User Name: sa

      - password: (pozostaw puste)

### 5. Zatrzymanie aplikacji

Aby zatrzymać kontenery, wciśnij Ctrl + C w terminalu lub wpisz:

```PowerShell
podman compose down
```

## Uruchomienie lokalne (bez kontenerów)

Jeśli chcesz uruchomić aplikację bezpośrednio na systemie operacyjnym:
Wymagania lokalne:

  - Zainstalowane JDK 25 (np. Eclipse Temurin 25 / Microsoft OpenJDK 25)

  - Zainstalowany Apache Maven

  - Zainstalowany Node.js (wersja 20 lub nowsza)

### 1. Konfiguracja proxy dla uruchomienia lokalnego

W pliku frontend/vite.config.js upewnij się, że adres target wskazuje na localhost (w wersji kontenerowej wskazuje na nazwę usługi http://backend:8080):

```JavaScript
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  }
}
```

### 2. Uruchomienie Backend (Spring Boot)

Otwórz terminal w folderze backend, wskaż ścieżkę do JDK 25 i uruchom aplikację:

```PowerShell
cd backend
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-25.0.1.8-hotspot"
mvn spring-boot:run
```

### 3. Uruchomienie Frontend (Vite)

Otwórz osobny terminal w folderze frontend, zainstaluj zależności i uruchom serwer deweloperski:

```PowerShell
cd frontend
npm install
npm run dev
```