#!/usr/bin/env bash
# Prepares a fresh Ubuntu 24.04 LTS server for PPF (Hostinger KVM 1 or any equivalent machine).
# Run once as root, then again whenever this script changes (every step is idempotent):
#
#   bootstrap-server.sh --github <owner>/<repository> --admin-key "<ssh public key>" [--domain <domain>]
#   bootstrap-server.sh --admin-key "<ssh public key>"   (before GitHub is linked: preproduction from
#                                                       bundles only, see ppf-deploy preproduction-bundle)
#   bootstrap-server.sh --lockdown      (only after logging in as ppf-admin with the key worked)
#
# What it does (cahier des charges, sections 3.5 and 5; CLAUDE.md, section 8):
#  1. system up to date, automatic security updates with a reboot at night if needed;
#  2. administrator account "ppf-admin", SSH by key only (root and passwords refused at --lockdown);
#  3. firewall: only SSH, HTTP(S) and WireGuard; firewall logging off (no IP address written);
#  4. system journal kept in memory for one day at most;
#  5. Docker Engine from Docker's own repository (key fingerprint checked), hardened daemon;
#  6. WireGuard tunnel 10.8.0.1/24 (keys generated here, never leave the server);
#  7. directories, generated passwords and environment files of the proxy, preproduction and
#     production (outside the repository, readable by root only);
#  8. public checkout of the repository, cosign (signature check of the images), the deployment
#     command /usr/local/sbin/ppf-deploy and the automatic update of the preproduction.
#
# Nothing here is a secret: every key and password is generated on the server.
set -euo pipefail

readonly ADMIN_USER="ppf-admin"
readonly WG_ADDRESS="10.8.0.1/24"
readonly WG_PORT="51820"
readonly REPO_DIR="/opt/ppf/repo"
readonly CONF_DIR="/etc/ppf"
readonly DATA_DIR="/srv/ppf"
# Docker's repository signing key (https://docs.docker.com/engine/install/ubuntu/).
readonly DOCKER_KEY_FINGERPRINT="9DC858229FC7DD38854AE2D88D81803C0EBFCD88"
# cosign, to check that an image was built and signed by the CI of the repository.
readonly COSIGN_VERSION="v3.1.4"
readonly COSIGN_SHA256="424e2c97d0b45fd7a6b608a87f090f35633f4c23ece5f092d48bba11aaf4bbfa"
readonly CADDY_IMAGE="caddy:2.11.9-alpine@sha256:0bbb055bda6793c999bde78b1b5ce3e89f829fb031af2cf50056ea1246559fee"

github=""
admin_key=""
domain="politisation-populaire-francaise.org"
lockdown=false

usage() {
    sed -n '2,8p' "$0" >&2
    exit 2
}

while [ $# -gt 0 ]; do
    case "$1" in
        --github) github="${2:-}"; shift 2 ;;
        --admin-key) admin_key="${2:-}"; shift 2 ;;
        --domain) domain="${2:-}"; shift 2 ;;
        --lockdown) lockdown=true; shift ;;
        *) usage ;;
    esac
done

log() { printf '\n== %s\n' "$*"; }

[ "$(id -u)" -eq 0 ] || { echo "Run as root." >&2; exit 1; }
. /etc/os-release
[ "${ID:-}" = "ubuntu" ] && [ "${VERSION_ID:-}" = "24.04" ] || { echo "Ubuntu 24.04 expected, found ${PRETTY_NAME:-unknown}." >&2; exit 1; }

# --- Lockdown: root and passwords refused, only the administrator key --------------------------
if [ "$lockdown" = true ]; then
    [ -s "/home/$ADMIN_USER/.ssh/authorized_keys" ] || { echo "No key for $ADMIN_USER: run the full bootstrap first." >&2; exit 1; }
    log "SSH: root login and passwords refused, $ADMIN_USER only"
    cat > /etc/ssh/sshd_config.d/00-ppf-hardening.conf <<EOF
# Written by infra/scripts/bootstrap-server.sh --lockdown. Read before the other drop-ins,
# so these values win over the hosting provider's defaults.
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
AuthenticationMethods publickey
AllowUsers $ADMIN_USER
X11Forwarding no
AllowAgentForwarding no
AllowTcpForwarding no
MaxAuthTries 3
LoginGraceTime 30
EOF
    sshd -t
    systemctl restart ssh
    # The keys the provider may have installed for root are no longer usable; remove them too.
    if [ -f /root/.ssh/authorized_keys ]; then
        mv /root/.ssh/authorized_keys "/root/.ssh/authorized_keys.disabled-$(date +%Y%m%d)"
    fi
    echo "Done. Keep this session open and check a new login as $ADMIN_USER before closing it."
    exit 0
fi

image_repo=""
signer_identity=""
if [ -n "$github" ]; then
    [[ "$github" =~ ^[A-Za-z0-9-]+/[A-Za-z0-9._-]+$ ]] || { echo "--github must look like owner/repository." >&2; exit 2; }
    owner_lower="$(printf '%s' "${github%%/*}" | tr '[:upper:]' '[:lower:]')"
    image_repo="ghcr.io/$owner_lower/ppf-server"
    signer_identity="https://github.com/$github/.github/workflows/ci.yml@refs/heads/main"
else
    echo "No --github: the preproduction will be deployed from bundles (ppf-deploy preproduction-bundle) until GitHub is linked; run this script again with --github then."
fi
export DEBIAN_FRONTEND=noninteractive

# --- 1. System and automatic security updates --------------------------------------------------
log "System packages"
apt-get update -q
apt-get -y -q -o Dpkg::Options::=--force-confold full-upgrade
apt-get -y -q install ca-certificates curl git gnupg jq ufw unattended-upgrades wireguard qrencode openssl

log "Automatic security updates"
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
APT::Periodic::AutocleanInterval "7";
EOF
cat > /etc/apt/apt.conf.d/52ppf-unattended-upgrades <<'EOF'
// Written by infra/scripts/bootstrap-server.sh. Security updates only (Ubuntu default origins),
// with a reboot at night when a kernel update needs it; the containers restart by themselves.
Unattended-Upgrade::Remove-Unused-Dependencies "true";
Unattended-Upgrade::Automatic-Reboot "true";
Unattended-Upgrade::Automatic-Reboot-Time "04:30";
EOF

# Memory safety net for a 4 GB machine: a small swap file, used only under pressure.
if ! swapon --show=NAME --noheadings | grep -q '^/swapfile$'; then
    log "Swap file (2 GB)"
    [ -f /swapfile ] || { fallocate -l 2G /swapfile; chmod 600 /swapfile; mkswap /swapfile >/dev/null; }
    swapon /swapfile
    grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
echo 'vm.swappiness=10' > /etc/sysctl.d/90-ppf.conf
sysctl -q --system

# --- 2. Administrator account -------------------------------------------------------------------
log "Administrator account $ADMIN_USER"
if ! id "$ADMIN_USER" >/dev/null 2>&1; then
    useradd --create-home --shell /bin/bash "$ADMIN_USER"
fi
# No password at all: the account is reached by key only and uses sudo without one.
passwd -l "$ADMIN_USER" >/dev/null
install -d -m 700 -o "$ADMIN_USER" -g "$ADMIN_USER" "/home/$ADMIN_USER/.ssh"
if [ -n "$admin_key" ]; then
    [[ "$admin_key" =~ ^ssh-ed25519\ [A-Za-z0-9+/=]+(\ .*)?$ ]] || { echo "--admin-key must be an ssh-ed25519 public key." >&2; exit 2; }
    touch "/home/$ADMIN_USER/.ssh/authorized_keys"
    grep -qxF "$admin_key" "/home/$ADMIN_USER/.ssh/authorized_keys" || echo "$admin_key" >> "/home/$ADMIN_USER/.ssh/authorized_keys"
fi
[ -s "/home/$ADMIN_USER/.ssh/authorized_keys" ] || { echo "Give --admin-key the first time." >&2; exit 2; }
chown "$ADMIN_USER:$ADMIN_USER" "/home/$ADMIN_USER/.ssh/authorized_keys"
chmod 600 "/home/$ADMIN_USER/.ssh/authorized_keys"
echo "$ADMIN_USER ALL=(ALL) NOPASSWD:ALL" > "/etc/sudoers.d/90-$ADMIN_USER"
chmod 440 "/etc/sudoers.d/90-$ADMIN_USER"
visudo -cqf "/etc/sudoers.d/90-$ADMIN_USER"

# --- 3. Firewall --------------------------------------------------------------------------------
log "Firewall"
ufw default deny incoming >/dev/null
ufw default allow outgoing >/dev/null
ufw allow 22/tcp >/dev/null
ufw allow 80/tcp >/dev/null
ufw allow 443/tcp >/dev/null
ufw allow 443/udp >/dev/null
ufw allow "$WG_PORT/udp" >/dev/null
# Blocked packets are not written to the journal: they would carry IP addresses.
ufw logging off >/dev/null
ufw --force enable >/dev/null
# Note: Docker publishes ports through its own rules; only the proxy publishes any (80, 443).

# --- 4. System journal: in memory, one day at most ---------------------------------------------
log "System journal"
install -d /etc/systemd/journald.conf.d
cat > /etc/systemd/journald.conf.d/90-ppf.conf <<'EOF'
# Written by infra/scripts/bootstrap-server.sh. System logs (which may hold the addresses of
# connection attempts) never reach the disk and are forgotten after one day.
[Journal]
Storage=volatile
RuntimeMaxUse=64M
MaxRetentionSec=1day
EOF
systemctl restart systemd-journald

# --- 5. Docker Engine ---------------------------------------------------------------------------
if ! command -v docker >/dev/null 2>&1; then
    log "Docker Engine"
    install -d -m 0755 /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /tmp/docker.asc
    actual_fingerprint="$(gpg --show-keys --with-colons /tmp/docker.asc 2>/dev/null | awk -F: '/^fpr:/ {print $10; exit}')"
    [ "$actual_fingerprint" = "$DOCKER_KEY_FINGERPRINT" ] || { echo "Unexpected Docker key fingerprint: $actual_fingerprint" >&2; exit 1; }
    mv /tmp/docker.asc /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $VERSION_CODENAME stable" \
        > /etc/apt/sources.list.d/docker.list
    apt-get update -q
    apt-get -y -q install docker-ce docker-ce-cli containerd.io docker-compose-plugin
fi
log "Docker daemon settings"
install -d /etc/docker
cat > /etc/docker/daemon.json <<'EOF'
{
  "log-driver": "local",
  "log-opts": { "max-size": "5m", "max-file": "3" },
  "no-new-privileges": true,
  "live-restore": true,
  "userland-proxy": false
}
EOF
systemctl enable --now docker >/dev/null 2>&1
systemctl restart docker
usermod -aG docker "$ADMIN_USER"
for network in ppf-prod-edge ppf-preprod-edge; do
    docker network inspect "$network" >/dev/null 2>&1 || docker network create "$network" >/dev/null
done
# Between the proxy and the approval pages of the signing service only (D-129, revision of 8 November
# 2026): internal, so that the signing service never gets a way out to the Internet.
for network in ppf-prod-approval ppf-preprod-approval; do
    docker network inspect "$network" >/dev/null 2>&1 || docker network create --internal "$network" >/dev/null
done

# --- 6. WireGuard -------------------------------------------------------------------------------
log "WireGuard tunnel"
install -d -m 700 /etc/wireguard
if [ ! -f /etc/wireguard/server.key ]; then
    (umask 077; wg genkey > /etc/wireguard/server.key)
    wg pubkey < /etc/wireguard/server.key > /etc/wireguard/server.pub
fi
if [ ! -f /etc/wireguard/wg0.conf ]; then
    (umask 077; cat > /etc/wireguard/wg0.conf <<EOF
# Written by infra/scripts/bootstrap-server.sh; peers are added by wg-add-peer.sh.
[Interface]
Address = $WG_ADDRESS
ListenPort = $WG_PORT
PrivateKey = $(cat /etc/wireguard/server.key)
EOF
    )
fi
systemctl enable --now wg-quick@wg0 >/dev/null 2>&1

# --- 7. Directories, passwords, environment files -----------------------------------------------
log "Environments (proxy, preproduction, production)"
install -d -m 0711 "$DATA_DIR"
install -d -m 0700 "$DATA_DIR/proxy" "$DATA_DIR/proxy/conf"
install -d -m 0755 "$DATA_DIR/proxy/conf/sites"

new_password() { openssl rand -hex 32; }

# One environment: .env, and the database password in two files (one per container user).
prepare_environment() {
    local env="$1" db="$2" server_uid="$3" editor_keys="$4" backoffice_origin="$5" public_url="$6" importer_uid="$7" ntfy_uid="$8" signer_uid="$9"
    local dir="$DATA_DIR/$env"
    install -d -m 0700 "$dir" "$dir/secrets"
    # Owner role (migrations only) and restricted role ppf_app (the running server), D-127.
    if [ ! -f "$dir/secrets/migrate_db_password" ]; then
        local owner app
        owner="$(new_password)"
        app="$(new_password)"
        (umask 077
         printf '%s' "$owner" > "$dir/secrets/db_password"
         printf '%s' "$owner" > "$dir/secrets/migrate_db_password"
         printf '%s' "$app" > "$dir/secrets/app_db_password"
         printf '%s' "$app" > "$dir/secrets/server_db_password")
    fi
    # PostgreSQL runs as 999 in its image; the server and the migrations as the environment's UID.
    chown 999:999 "$dir/secrets/db_password" "$dir/secrets/app_db_password"
    chown "$server_uid:$server_uid" "$dir/secrets/server_db_password" "$dir/secrets/migrate_db_password"
    chmod 400 "$dir/secrets/db_password" "$dir/secrets/app_db_password" "$dir/secrets/server_db_password" "$dir/secrets/migrate_db_password"
    if [ ! -f "$dir/.env" ]; then
        (umask 077; cat > "$dir/.env" <<EOF
# Written by infra/scripts/bootstrap-server.sh (model: infra/env/$env.env.example).
PPF_SECRETS_DIR=$dir/secrets
POSTGRES_DB=$db
POSTGRES_USER=$db
PPF_EDITOR_KEYS=$editor_keys
EOF
        )
    fi
    # Settings added after the first run of this script: appended once, never overwritten.
    grep -q '^PPF_BACKOFFICE_ORIGIN=' "$dir/.env" || echo "PPF_BACKOFFICE_ORIGIN=$backoffice_origin" >> "$dir/.env"
    grep -q '^PPF_AUDIT_PUBLIC_KEY=' "$dir/.env" || echo "PPF_AUDIT_PUBLIC_KEY=" >> "$dir/.env"
    grep -q '^PPF_PUBLIC_URL=' "$dir/.env" || echo "PPF_PUBLIC_URL=$public_url" >> "$dir/.env"
    # Seeds of the vote keys (Décider, D-110): one file of 32 random bytes per version, read-only for the
    # server's UID; version 1 is created here, later ones by ppf-vote-seed rotate.
    install -d -m 0500 -o "$server_uid" -g "$server_uid" "$dir/vote_seeds"
    if [ ! -f "$dir/vote_seeds/1" ]; then
        (umask 077; head -c 32 /dev/urandom > "$dir/vote_seeds/1")
    fi
    chown "$server_uid:$server_uid" "$dir/vote_seeds"/*
    chmod 400 "$dir/vote_seeds"/*
    # Signing service (D-129): its data, its key-encryption key, and the token shared with the server.
    install -d -m 0700 -o "$signer_uid" -g "$signer_uid" "$dir/signer"
    if [ ! -f "$dir/secrets/signer_kek" ]; then
        (umask 077; openssl rand -hex 32 | tr -d '\n' > "$dir/secrets/signer_kek")
    fi
    if [ ! -f "$dir/secrets/signer_client_token" ]; then
        local token
        token="$(openssl rand -hex 32)"
        (umask 077; printf '%s' "$token" > "$dir/secrets/signer_client_token"; printf '%s' "$token" > "$dir/secrets/server_signer_token")
    fi
    chown "$signer_uid:$signer_uid" "$dir/secrets/signer_kek" "$dir/secrets/signer_client_token"
    chown "$server_uid:$server_uid" "$dir/secrets/server_signer_token"
    chmod 400 "$dir/secrets/signer_kek" "$dir/secrets/signer_client_token" "$dir/secrets/server_signer_token"
    grep -q '^PPF_SIGNER_ROOT_PUBKEY=' "$dir/.env" || echo "PPF_SIGNER_ROOT_PUBKEY=" >> "$dir/.env"
    # The approval pages are served by the signing service itself, under a name of their own below the
    # back-office's (same passkeys, a different origin the main server cannot speak for).
    local backoffice_host="${backoffice_origin#https://}"
    grep -q '^PPF_SIGNER_RP_ID=' "$dir/.env" || echo "PPF_SIGNER_RP_ID=$backoffice_host" >> "$dir/.env"
    grep -q '^PPF_SIGNER_APPROVAL_ORIGIN=' "$dir/.env" || echo "PPF_SIGNER_APPROVAL_ORIGIN=https://signature.$backoffice_host" >> "$dir/.env"
    # Testimonies (Agir, M5): encrypted files written by the server only.
    install -d -m 0700 -o "$server_uid" -g "$server_uid" "$dir/testimonies"
    grep -q '^PPF_MODERATION_KEY=' "$dir/.env" || echo "PPF_MODERATION_KEY=" >> "$dir/.env"
    grep -q '^PPF_EVIDENCE_KEY=' "$dir/.env" || echo "PPF_EVIDENCE_KEY=" >> "$dir/.env"
    # Contact and claims (M30): the public key of the inbox of the team, empty until it is created (cases closed).
    grep -q '^PPF_CONTACT_INBOX_KEY=' "$dir/.env" || echo "PPF_CONTACT_INBOX_KEY=" >> "$dir/.env"
    # Push wake-ups (D-078): ntfy's data (topics, 12-hour cache) and the VAPID private key of the server.
    install -d -m 0700 -o "$ntfy_uid" -g "$ntfy_uid" "$dir/ntfy"
    if [ ! -f "$dir/secrets/vapid_private_key" ]; then
        (umask 077; openssl ecparam -name prime256v1 -genkey -noout | openssl pkcs8 -topk8 -nocrypt -out "$dir/secrets/vapid_private_key")
    fi
    chown "$server_uid:$server_uid" "$dir/secrets/vapid_private_key"
    chmod 400 "$dir/secrets/vapid_private_key"
    # Private anti-fraud settings (model: infra/env/private.env.example), empty until set by hand.
    [ -f "$dir/private.env" ] || (umask 077; echo "# Private anti-fraud settings, see infra/env/private.env.example." > "$dir/private.env")
    # Parliamentary open data: the importer of this environment writes, the server reads; the import
    # key file stays empty until generated (ppf-open-data-import <environment> keygen).
    install -d -m 0755 -o "$importer_uid" -g "$importer_uid" "$dir/open-data"
    [ -f "$dir/secrets/import_key" ] || (umask 077; : > "$dir/secrets/import_key")
    chown "$importer_uid:$importer_uid" "$dir/secrets/import_key"
    chmod 400 "$dir/secrets/import_key"
    # Presentations of the presidential election (Voir, VO-37): the data.gouv.fr dataset of 2027, read by the
    # importer, empty until the decree of convocation (nothing is asked while it is empty).
    grep -q '^PPF_SPONSORSHIPS_DATASET=' "$dir/.env" || echo "PPF_SPONSORSHIPS_DATASET=" >> "$dir/.env"
}
# The example content signer (EditorialTrust.EXAMPLE_EDITOR) publishes the [EXEMPLE] contents of the
# preproduction only. The production starts with no editor key.
prepare_environment preproduction ppf_preprod 10002 "23484985405d16929a93f9aea29135678ad0ced909bf5a264f7b923d619b012f" "https://equipe.preprod.$domain" "https://preprod.$domain" 10004 10006 10008
prepare_environment production ppf 10001 "" "https://equipe.$domain" "https://$domain" 10003 10005 10007

if [ ! -f "$DATA_DIR/proxy/.env" ]; then
    # Password of the preproduction web pages (D-072). Chosen and typed by the owner herself at this
    # moment, in the terminal of the deployment (decision of 7 November 2026): it is never written in
    # the repository, in a message or in a file; only its bcrypt hash is kept, in the proxy settings.
    [ -t 0 ] || { echo "The preproduction password is typed by the owner: run this script in an interactive terminal." >&2; exit 1; }
    while true; do
        printf 'Mot de passe des pages web de la préproduction (12 caractères au moins, rien ne s'"'"'affiche) : '
        IFS= read -rs preprod_password; echo
        printf 'Retapez-le : '
        IFS= read -rs preprod_confirm; echo
        if [ "${#preprod_password}" -lt 12 ]; then
            echo "Trop court : 12 caractères au moins."
        elif [ "$preprod_password" != "$preprod_confirm" ]; then
            echo "Les deux saisies diffèrent, recommencez."
        else
            break
        fi
    done
    unset preprod_confirm
    # The password goes to Caddy through its standard input, never on a command line.
    preprod_hash="$(printf '%s\n' "$preprod_password" | docker run --rm -i "$CADDY_IMAGE" caddy hash-password)"
    unset preprod_password
    [[ "$preprod_hash" == \$2* ]] || { echo "Caddy did not return a bcrypt hash." >&2; exit 1; }
    (umask 077; cat > "$DATA_DIR/proxy/.env" <<EOF
# Written by infra/scripts/bootstrap-server.sh (model: infra/env/proxy.env.example).
PPF_DOMAIN=$domain
PREPROD_BASIC_AUTH_USER=equipe
# Single quotes: the bcrypt hash holds "\$" signs, which Compose must not interpolate.
PREPROD_BASIC_AUTH_HASH='$preprod_hash'
EOF
    )
    echo "Preproduction web password set (identifiant : equipe). Only its hash is kept."
fi

# --- 8. Repository, cosign, deployment command, automatic update of the preproduction ------------
log "Repository checkout (public, read only)"
install -d -m 0755 /opt/ppf
if [ -n "$github" ]; then
    if [ ! -d "$REPO_DIR/.git" ]; then
        git clone --quiet "https://github.com/$github.git" "$REPO_DIR"
    else
        git -C "$REPO_DIR" fetch --quiet origin
    fi
fi

log "cosign $COSIGN_VERSION"
if ! cosign version 2>/dev/null | grep -q "${COSIGN_VERSION#v}"; then
    curl -fsSL "https://github.com/sigstore/cosign/releases/download/$COSIGN_VERSION/cosign-linux-amd64" -o /tmp/cosign
    echo "$COSIGN_SHA256  /tmp/cosign" | sha256sum -c --quiet
    install -m 0755 /tmp/cosign /usr/local/bin/cosign
    rm -f /tmp/cosign
fi

log "Deployment command"
install -d -m 0755 "$CONF_DIR"
cat > "$CONF_DIR/deploy.conf" <<EOF
# Written by infra/scripts/bootstrap-server.sh. Read by /usr/local/sbin/ppf-deploy.
PPF_GITHUB=$github
PPF_IMAGE_REPO=$image_repo
PPF_SIGNER_IDENTITY=$signer_identity
PPF_REPO_DIR=$REPO_DIR
PPF_DATA_DIR=$DATA_DIR
EOF
# The installed copy is the one that runs (also from the timer); it changes only when this
# bootstrap runs again, never by itself from the repository.
script_dir="$(cd "$(dirname "$0")" && pwd)"
install -m 0755 "$script_dir/deploy.sh" /usr/local/sbin/ppf-deploy
install -m 0755 "$script_dir/wg-add-peer.sh" /usr/local/sbin/ppf-wg-add-peer
install -m 0755 "$script_dir/backoffice.sh" /usr/local/sbin/ppf-backoffice
install -m 0755 "$script_dir/wg-remove-peer.sh" /usr/local/sbin/ppf-wg-remove-peer
install -m 0644 "$script_dir/../systemd/ppf-preprod-update.service" /etc/systemd/system/
install -m 0644 "$script_dir/../systemd/ppf-preprod-update.timer" /etc/systemd/system/
install -m 0755 "$script_dir/open-data-import.sh" /usr/local/sbin/ppf-open-data-import
install -m 0644 "$script_dir/../systemd/ppf-open-data-daily@.service" "$script_dir/../systemd/ppf-open-data-daily@.timer" /etc/systemd/system/
install -m 0644 "$script_dir/../systemd/ppf-open-data-weekly@.service" "$script_dir/../systemd/ppf-open-data-weekly@.timer" /etc/systemd/system/
install -m 0755 "$script_dir/vote-seed.sh" /usr/local/sbin/ppf-vote-seed
# The import timers are enabled per environment once its import key exists:
#   systemctl enable --now ppf-open-data-daily@preproduction.timer ppf-open-data-weekly@preproduction.timer
systemctl daemon-reload
# The automatic update follows the signed images of main: only once GitHub is linked.
if [ -n "$github" ]; then
    systemctl enable ppf-preprod-update.timer >/dev/null 2>&1
else
    systemctl disable ppf-preprod-update.timer >/dev/null 2>&1 || true
fi

log "Done"
cat <<EOF
Server prepared. Next steps (docs/guides/serveur-hostinger-kvm1.md):
  1. check a login as $ADMIN_USER with the key, then: bootstrap-server.sh --lockdown
  2. ppf-wg-add-peer <device> for each device of the team
  3. ppf-deploy preproduction, then: systemctl start ppf-preprod-update.timer
  4. ppf-backoffice preproduction bootstrap <pseudonyme> for the first team account
WireGuard public key of the server: $(cat /etc/wireguard/server.pub)
EOF
