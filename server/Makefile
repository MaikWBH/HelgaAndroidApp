# ── Konfiguration (einmalig anpassen) ─────────────────────────────────────────
# Beispiel: SERVER_HOST = maik@192.168.1.100
SERVER_HOST = casaos@192.168.1.x
SERVER_PATH = /DATA/AppData/helga-sync
COMPOSE     = docker compose -f docker-compose.prod.yml
# ─────────────────────────────────────────────────────────────────────────────

.PHONY: deploy logs backup restore migrate status dev help

help:
	@echo "Verfügbare Befehle:"
	@echo "  make deploy           – Server aktualisieren (git pull + rebuild)"
	@echo "  make logs             – Live-Logs vom Server"
	@echo "  make status           – Container-Status + Health-Check"
	@echo "  make backup           – DB + Bilder sichern"
	@echo "  make restore FILE=... – Backup einspielen"
	@echo "  make migrate          – Helga-Daten importieren (einmalig)"
	@echo "  make dev              – Lokal starten (kein SSH)"

deploy:
	ssh $(SERVER_HOST) "cd $(SERVER_PATH) && git pull && $(COMPOSE) up -d --build"

logs:
	ssh $(SERVER_HOST) "cd $(SERVER_PATH) && $(COMPOSE) logs -f"

status:
	ssh $(SERVER_HOST) "cd $(SERVER_PATH) && $(COMPOSE) ps && curl -s http://localhost:8000/api/health"

backup:
	ssh $(SERVER_HOST) "\
		mkdir -p $(SERVER_PATH)/data/backups && \
		tar -czf $(SERVER_PATH)/data/backups/backup-\$$(date +%Y%m%d-%H%M%S).tar.gz \
		    -C $(SERVER_PATH)/data recipes.db images/ 2>/dev/null || true && \
		echo 'Backup erstellt.'"

restore:
ifndef FILE
	$(error FILE ist nicht gesetzt. Aufruf: make restore FILE=data/backups/backup-xyz.tar.gz)
endif
	ssh $(SERVER_HOST) "\
		cd $(SERVER_PATH) && \
		$(COMPOSE) stop && \
		tar -xzf $(FILE) -C data/ && \
		$(COMPOSE) start && \
		echo 'Wiederhergestellt.'"

migrate:
	# Pfad zur Helga-SQLite anpassen (Standard-CasaOS: /DATA/AppData/helga/data/recipes.db)
	ssh $(SERVER_HOST) "\
		cd $(SERVER_PATH) && \
		$(COMPOSE) exec helga-sync python scripts/migrate_from_helga.py \
		    --src /DATA/AppData/helga/data/recipes.db --dst data/recipes.db"

dev:
	docker compose up --build
