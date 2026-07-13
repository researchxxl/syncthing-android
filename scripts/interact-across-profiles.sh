#!/system/bin/sh

# SPDX-FileCopyrightText: 2026 Andrew Gunnerson
# SPDX-License-Identifier: GPL-3.0-only

# modified to fit this repo and app

set -e

change_permission() {
    local action=${1}
    local package=${2}
    local permission=${3}

    pm list users | while IFS= read -r line; do
        if [[ "${line}" != *UserInfo* ]]; then
            continue
        fi

        line=${line#*\{}
        user_id=${line%%:*}

        line=${line#*:}
        user_name=${line%%:*}

        echo "Checking user ${user_id}: ${user_name}"

        if ! pm path --user "${user_id}" "${package}" >/dev/null; then
            echo "- Skipping: ${package} not installed in this user"
            continue
        fi

        echo "- Updating permission: ${action}"
        pm "${action}" --user "${user_id}" "${package}" "${permission}"

        echo "- Crashing app to trigger restart"
        am crash --user "${user_id}" "${package}"
    done
}

if [[ "${#}" != 1 || ("${1}" != grant && "${1}" != revoke) ]]; then
    echo >&2 "Usage: ${0} <grant|revoke>"
    exit 1
fi

change_permission "${1}" com.github.catfriend1.syncthingfork.debug android.permission.INTERACT_ACROSS_USERS
change_permission "${1}" com.github.catfriend1.syncthingfork android.permission.INTERACT_ACROSS_USERS
