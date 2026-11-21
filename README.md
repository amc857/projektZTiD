# Projekt ZTiD

Aplikacja webowa z uwierzytelnianiem dwuskładnikowym (TOTP), obsługą JWT oraz zarządzaniem plikami, składająca się z backendu w Spring Boot oraz frontendu w React (Vite).

---

## Wymagania technologiczne

* **Backend:** Java 25 (JDK 25), Spring Boot 3.5.x, Maven, baza danych H2 (in-memory)
* **Frontend:** Node.js 20+, React, Vite
* **Konteneryzacja:** Podman (z `podman-compose`) lub Docker

---

## Uruchomienie projektu (Podman / Kontenery) — Zalecane

Projekt jest w pełni skonfigurowany do działania w kontenerach, co nie wymaga lokalnej instalacji Javy, Mavena ani Node.js.

### 1. Wymagania wstępne
Upewnij się, że masz zainstalowanego **Podmana** (np. poprzez *Podman Desktop*) i włączoną maszynę wirtualną Podmana (na systemie Windows/macOS):
```powershell
podman machine start
```

### 2. Budowanie i uruchomienie kontenerów
W głównym katalogu projektu (tam, gdzie znajdują się pliki `Dockerfile` oraz `docker-compose.yml`) uruchom:

```powershell
podman compose up --build
```
*(Jeśli używasz starszej wersji lub zewnętrznego narzędzia, możesz również użyć `podman-compose up --build` lub `docker-compose up --build`).*

### 3. Dostęp do aplikacji
Po zbudowaniu i uruchomieniu kontenerów poszczególne elementy są dostępne pod adresami:
* **Frontend (Aplikacja React):** [http://localhost:5173](http://localhost:5173)
* **Backend API:** [http://localhost:8080](http://localhost:8080)
* **Konsola bazy danych H2:** [http://localhost:8080/h2-console](http://localhost:8080/h2-console)
  * *JDBC URL:* `jdbc:h2:mem:ztiddb`
  * *User Name:* `sa`
  * *Password:* *(pozostaw puste)*

### 4. Zatrzymanie aplikacji
Aby zatrzymać kontenery, wciśnij `Ctrl + C` w terminalu lub wpisz:
```powershell
podman compose down
```

---

## Uruchomienie lokalne (bez kontenerów)

Jeśli chcesz uruchomić aplikację bezpośrednio na systemie operacyjnym:

### Wymagania lokalne:
* Zainstalowane **JDK 25** (np. Eclipse Temurin 25 / Microsoft OpenJDK 25)
* Zainstalowany **Apache Maven**
* Zainstalowany **Node.js** (wersja 20 lub nowsza)

### 1. Konfiguracja proxy dla uruchomienia lokalnego
W pliku `frontend/vite.config.js` upewnij się, że adres `target` wskazuje na `localhost` (w wersji kontenerowej wskazuje na nazwę usługi `http://backend:8080`):
```javascript
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  }
}
```

### 2. Uruchomienie Backend (Spring Boot)
Otwórz terminal w folderze `backend`, wskaż ścieżkę do JDK 25 i uruchom aplikację:
```powershell
cd backend
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-25.0.1.8-hotspot"
mvn spring-boot:run
```

### 3. Uruchomienie Frontend (Vite)
Otwórz osobny terminal w folderze `frontend`, zainstaluj zależności i uruchom serwer deweloperski:
```powershell
cd frontend
npm install
npm run dev
```