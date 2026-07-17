# Application service hardening

The production Spring Boot process must not run as the interactive SSH deployment account. Provision a dedicated non-login account before installing `deploy/systemd/app.service`:

```bash
sudo addgroup --system noviis-app
sudo adduser --system --ingroup noviis-app --no-create-home --shell /usr/sbin/nologin noviis-app
sudo adduser --disabled-password --gecos '' noviis-deploy
sudo install -d -o noviis-app -g noviis-app -m 0750 /opt/app/logs /opt/app/uploads
sudo install -d -o root -g root -m 0755 /opt/app/backend
sudo install -d -o noviis-deploy -g noviis-deploy -m 0750 /opt/app/backend/releases
sudo install -d -o noviis-deploy -g noviis-deploy -m 0750 /var/www/releases/frontend
sudo install -o root -g root -m 0755 deploy/scripts/activate-backend-release.sh /usr/local/sbin/activate-noviis-backend
sudo install -o root -g root -m 0755 deploy/scripts/activate-frontend-release.sh /usr/local/sbin/activate-noviis-frontend
sudo install -o root -g root -m 0440 deploy/sudoers/noviis-deploy /etc/sudoers.d/noviis-deploy
sudo visudo -cf /etc/sudoers.d/noviis-deploy
sudo touch /etc/noviis/app.env
sudo chown root:root /etc/noviis/app.env
sudo chmod 0600 /etc/noviis/app.env
```

Populate `/etc/noviis/app.env` through the host secret-management procedure. Keep it a regular, non-symlink file owned by `root:root` with mode `0600`. The unit pins `SPRING_PROFILES_ACTIVE=prod`, and both the unit and activation script fail closed on an invalid environment file.

The workflow connects only as `noviis-deploy`. Its passwordless sudo surface is limited to the two root-owned activation entrypoints installed above; uploaded release content is never executable through sudo. Reinstall the reviewed entrypoints as root whenever those tracked scripts change. The `noviis-app` runtime account has no sudo access and cannot write release roots.

After installing the unit, verify it before enabling:

```bash
sudo systemd-analyze verify /etc/systemd/system/app.service
sudo systemd-analyze security app.service
sudo systemctl daemon-reload
sudo systemctl enable --now app.service
curl -fsS http://127.0.0.1:8081/actuator/health
```

The sandbox permits writes only to `/opt/app/logs` and `/opt/app/uploads`. Any new local persistent path must be reviewed and added explicitly instead of weakening `ProtectSystem`.
