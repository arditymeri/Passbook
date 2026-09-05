FROM mcr.microsoft.com/devcontainers/java:1-21-bookworm

# The image ships an apt source for yarn whose signing key is no longer in the keyring
# (NO_PUBKEY 62D54FD4003F6525). Every feature install starts with `apt-get update`, which then
# fails with exit 100 and takes the whole container build down — Codespaces falls back to a
# recovery container with no Docker in it. Nothing here uses yarn, so drop the source.
RUN grep -rl 'dl.yarnpkg.com' /etc/apt/sources.list /etc/apt/sources.list.d/ 2>/dev/null \
    | xargs -r rm -f
