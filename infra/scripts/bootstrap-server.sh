#!/usr/bin/env bash
# Prepares a fresh Ubuntu 24.04 LTS server for PPF (Hostinger KVM 1 or any equivalent machine).
# Run once as root, then again whenever this script changes (every step is idempotent):
#
#   bootstrap-server.sh --github <owner>/<repository> --admin-key "<ssh public key>" [--domain <domain>]
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

[ -n "$github" ] || usage
[[ "$github" =~ ^[A-Za-z0-9-]+/[A-Za-z0-9._-]+$ ]] || { echo "--github must look like owner/repository." >&2; exit 2; }
owner_lower="$(printf '%s' "${github%%/*}" | tr '[:upper:]' '[:lower:]')"
image_repo="ghcr.io/$owner_lower/ppf-server"
signer_identity="https://github.com/$github/.github/workflows/ci.yml@refs/heads/main"
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
    local env="$1" db="$2" server_uid="$3" editor_keys="$4" backoffice_origin="$5"
    local dir="$DATA_DIR/$env"
    install -d -m 0700 "$dir" "$dir/secrets"
    if [ ! -f "$dir/secrets/db_password" ]; then
        local password
        password="$(new_password)"
        (umask 077; printf '%s' "$password" > "$dir/secrets/db_password"; printf '%s' "$password" > "$dir/secrets/server_db_password")
    fi
    # PostgreSQL runs as 999 in its image; the server as its own UID.
    chown 999:999 "$dir/secrets/db_password"
    chown "$server_uid:$server_uid" "$dir/secrets/server_db_password"
    chmod 400 "$dir/secrets/db_password" "$dir/secrets/server_db_password"
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
}
# The example content signer (EditorialTrust.EXAMPLE_EDITOR) publishes the [EXEMPLE] contents of the
# preproduction only. The production starts with no editor key.
prepare_environment preproduction ppf_preprod 10002 "23484985405d16929a93f9aea29135678ad0ced909bf5a264f7b923d619b012f" "https://equipe.preprod.$domain"
prepare_environment production ppf 10001 "" "https://equipe.$domain"

if [ ! -f "$DATA_DIR/proxy/.env" ]; then
    # Password of the preproduction web pages, generated here and shown to the owner once.
    preprod_password="$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-20)"
    preprod_hash="$(docker run --rm "$CADDY_IMAGE" caddy hash-password --plaintext "$preprod_password")"
    (umask 077; cat > "$DATA_DIR/proxy/.env" <<EOF
# Written by infra/scripts/bootstrap-server.sh (model: infra/env/proxy.env.example).
PPF_DOMAIN=$domain
PREPROD_BASIC_AUTH_USER=equipe
# Single quotes: the bcrypt hash holds "\$" signs, which Compose must not interpolate.
PREPROD_BASIC_AUTH_HASH='$preprod_hash'
EOF
    printf 'Préproduction PPF\nIdentifiant : equipe\nMot de passe : %s\n' "$preprod_password" > /root/ppf-preprod-password.txt)
    unset preprod_password
    echo "Preproduction web password written to /root/ppf-preprod-password.txt (give it to the owner, then delete the file)."
fi

# --- 8. Repository, cosign, deployment command, automatic update of the preproduction ------------
log "Repository checkout (public, read only)"
install -d -m 0755 /opt/ppf
if [ ! -d "$REPO_DIR/.git" ]; then
    git clone --quiet "https://github.com/$github.git" "$REPO_DIR"
else
    git -C "$REPO_DIR" fetch --quiet origin
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
install -m 0644 "$script_dir/../systemd/ppf-preprod-update.service" /etc/systemd/system/
install -m 0644 "$script_dir/../systemd/ppf-preprod-update.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable ppf-preprod-update.timer >/dev/null 2>&1

log "Done"
cat <<EOF
Server prepared. Next steps (docs/guides/serveur-hostinger-kvm1.md):
  1. check a login as $ADMIN_USER with the key, then: bootstrap-server.sh --lockdown
  2. ppf-wg-add-peer <device> for each device of the team
  3. ppf-deploy preproduction, then: systemctl start ppf-preprod-update.timer
  4. ppf-backoffice preproduction bootstrap <pseudonyme> for the first team account
WireGuard public key of the server: $(cat /etc/wireguard/server.pub)
EOF
