#!/usr/bin/env python3
"""Добавление тестовых пользователей в локальный OpenLDAP без изменения существующих."""
import argparse
import base64
import getpass
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
from datetime import datetime

BASE = "dc=example,dc=local"
GROUPS = {"product": "hub-product", "sales": "hub-sales", "head": "hub-head"}
LOGIN = re.compile(r"[a-z0-9][a-z0-9._-]{0,63}\Z")


def docker(*args, data=None):
    return subprocess.run(["sudo", "docker", "compose", "exec", "-T", "ldap", *args],
                          input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)


def ldap(command, data=None):
    # Пароль администратора берется внутри контейнера и не попадает в аргументы процесса.
    wrapper = ('set -eu; umask 077; p=$(mktemp); trap \'rm -f "$p"\' EXIT; '
               'printf %s "$LDAP_ADMIN_PASSWORD" > "$p"; '
               'exec_cmd=$1; shift; "$exec_cmd" -x -H ldap://localhost:389 '
               '-D "cn=admin,dc=example,dc=local" -y "$p" "$@"')
    return docker("sh", "-c", wrapper, "sh", *command, data=data)


def fail(message):
    print(message, file=sys.stderr)
    sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--role", required=True, choices=GROUPS)
    parser.add_argument("logins", nargs="+")
    args = parser.parse_args()
    logins = list(dict.fromkeys(s.lower() for s in args.logins))
    if any(not LOGIN.fullmatch(s) for s in logins):
        fail("Некорректный логин. Разрешены латинские буквы, цифры, точка, дефис и подчеркивание.")
    password = getpass.getpass("Пароль для новых пользователей: ")
    if not password:
        fail("Пустой пароль не допускается.")
    domain = docker("printenv", "LDAP_DOMAIN")
    if domain.returncode or domain.stdout.strip() != b"example.local":
        fail("Каталог не похож на тестовый example.local. Изменения не внесены.")
    group = f"cn={GROUPS[args.role]},ou=groups,{BASE}"
    for dn in (BASE, f"ou=people,{BASE}", group):
        result = ldap(["ldapsearch", "-LLL", "-b", dn, "-s", "base", "(objectClass=*)", "dn"])
        if result.returncode:
            fail("Не удалось проверить тестовый каталог или группу. Изменения не внесены.")
    backup_dir = Path.home() / "producthub-ldap-backups"
    backup_dir.mkdir(mode=0o700, exist_ok=True)
    backup_file = backup_dir / (datetime.now().strftime("ldap-%Y%m%d-%H%M%S-%f") + ".ldif")
    backup = docker("slapcat", "-n", "1")
    if backup.returncode or not backup.stdout:
        fail("Не удалось создать резервную копию LDAP. Изменения не внесены.")
    fd = os.open(backup_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "wb") as out:
        out.write(backup.stdout)
    print(f"Резервная копия LDAP: {backup_file}")
    for login in logins:
        dn = f"uid={login},ou=people,{BASE}"
        found = ldap(["ldapsearch", "-LLL", "-b", BASE, "-s", "sub", f"(uid={login})", "dn"])
        if found.returncode:
            fail(f"Ошибка поиска {login}; выполнение остановлено.")
        if found.stdout.strip():
            print(f"Пропущен существующий пользователь: {login}")
            continue
        salt = os.urandom(16)
        digest = hashlib.sha1(password.encode("utf-8") + salt).digest()
        hashed = "{SSHA}" + base64.b64encode(digest + salt).decode("ascii")
        entry = (f"dn: {dn}\nobjectClass: inetOrgPerson\nuid: {login}\n"
                 f"cn: {login}\nsn: {login}\nuserPassword: {hashed}\n\n").encode()
        result = ldap(["ldapadd"], data=entry)
        if result.returncode:
            fail(f"Не удалось создать {login}; выполнение остановлено.")
        change = (f"dn: {group}\nchangetype: modify\nadd: member\nmember: {dn}\n\n").encode()
        result = ldap(["ldapmodify"], data=change)
        if result.returncode:
            fail(f"Создан {login}, но не добавлен в {group}; проверьте группу вручную перед повтором.")
        print(f"Создан: {login} ({GROUPS[args.role]})")


if __name__ == "__main__":
    main()
