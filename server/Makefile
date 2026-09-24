# ── Konfiguration (einmalig anpassen) ─────────────────────────────────────────
# Produktiv läuft der Server als Submodule im Homelab-Repo (server_home_lab,
# apps/helga). Welche Version dort läuft, legt der Submodule-Commit fest --
# ausgerollt wird per `scripts/deploy.sh` im Homelab-Repo, nicht von hier.
SERVER_HOST = maik@192.168.178.68
HOMELAB_PATH = /home/maik/repo/server_home_lab
COMPOSE     = docker compose
SERVICE     = helga-sync
# = HELGA_DATA_DIR aus der .env des Homelab-Repos
DATA_PATH   = /home/maik/repo/homelab-data/helga
# ─────────────────────────────────────────────────────────────────────────────

.PHONY: deploy logs backup restore migrate status dev help

help:
	@echo "Verfügbare Befehle:"
	@echo "  make deploy           – Hinweis: Deploy läuft über server_home_lab"
	@echo "  make logs             – Live-Logs vom Server"
	@echo "  make status           – Container-Status + Health-Check"
	@echo "  make backup           – DB + Bilder sichern"
	@echo "  make restore FILE=... – Backup einspielen"
	@echo "  make migrate          – Helga-Daten importieren (einmalig)"
	@echo "  make dev              – Lokal starten (kein SSH)"

deploy:
	@echo "Deploy läuft über das Homelab-Repo:"
	@echo "  1. hier taggen + pushen (git tag vX.Y.Z && git push --tags)"
	@echo "  2. in server_home_lab: scripts/app.sh bump helga vX.Y.Z, committen, releasen"
	@echo "  3. auf dem Server: scripts/deploy.sh"

logs:
	ssh $(SERVER_HOST) "cd $(HOMELAB_PATH) && $(COMPOSE) logs -f $(SERVICE)"

status:
	ssh $(SERVER_HOST) "cd $(HOMELAB_PATH) && $(COMPOSE) ps $(SERVICE) && curl -s http://localhost:8000/api/health"

backup:
	ssh $(SERVER_HOST) "\
		mkdir -p $(DATA_PATH)/backups && \
		tar -czf $(DATA_PATH)/backups/backup-\$$(date +%Y%m%d-%H%M%S).tar.gz \
		    -C $(DATA_PATH) recipes.db images/ 2>/dev/null || true && \
		echo 'Backup erstellt.'"

restore:
ifndef FILE
	$(error FILE ist nicht gesetzt. Aufruf: make restore FILE=backups/backup-xyz.tar.gz (relativ zu DATA_PATH))
endif
	ssh $(SERVER_HOST) "\
		cd $(HOMELAB_PATH) && \
		$(COMPOSE) stop $(SERVICE) && \
		tar -xzf $(DATA_PATH)/$(FILE) -C $(DATA_PATH) && \
		$(COMPOSE) start $(SERVICE) && \
		echo 'Wiederhergestellt.'"

migrate:
	# Pfad zur Helga-SQLite anpassen (Standard-CasaOS: /DATA/AppData/helga/data/recipes.db)
	ssh $(SERVER_HOST) "\
		cd $(HOMELAB_PATH) && \
		$(COMPOSE) exec $(SERVICE) python scripts/migrate_from_helga.py \
		    --src /DATA/AppData/helga/data/recipes.db --dst data/recipes.db"

dev:
	docker compose up --build
