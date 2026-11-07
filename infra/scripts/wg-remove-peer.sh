#!/usr/bin/env bash
# Removes one device from the WireGuard tunnel at once (lost or stolen device, member cut from the
# back-office). Installed as /usr/local/sbin/ppf-wg-remove-peer by bootstrap-server.sh, run as root:
#
#   ppf-wg-remove-peer <device-name>
#
# The device can no longer reach the preproduction nor the back-office, from the next packet on.
set -euo pipefail

readonly CONF=/etc/wireguard/wg0.conf

name="${1:-}"
[[ "$name" =~ ^[a-z0-9-]{3,40}$ ]] || { echo "usage: $0 <device-name>" >&2; exit 2; }
[ "$(id -u)" -eq 0 ] || { echo "Run as root." >&2; exit 1; }
grep -q "^# device: $name$" "$CONF" || { echo "No device named $name." >&2; exit 1; }

# Drop the [Peer] block that holds "# device: <name>" (blocks are separated by blank lines).
umask 077
awk -v target="# device: $name" '
    BEGIN { RS = ""; ORS = "\n\n" }
    index($0, target) == 0 { print }
' "$CONF" > "$CONF.new"
mv "$CONF.new" "$CONF"
wg syncconf wg0 <(wg-quick strip wg0)
rm -f "/root/wireguard-peers/$name.conf" "/root/wireguard-peers/$name.png"
echo "Device $name removed from the tunnel."
