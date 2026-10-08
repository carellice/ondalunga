#!/bin/bash
#
# Doppio click per pubblicare una nuova versione di Onda Lunga:
#   1. incrementa la versione (se quella corrente è già stata pubblicata)
#   2. compila l'APK di release firmato
#   3. committa e pusha il cambio di versione
#   4. crea la release su GitHub con l'APK allegato
#   5. cancella le release precedenti (e i loro tag)
#
set -euo pipefail
cd "$(dirname "$0")"
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"

finish() {
    local code=$?
    echo
    if [ $code -eq 0 ]; then echo "✅ Fatto."; else echo "❌ Pubblicazione non riuscita (vedi sopra)."; fi
    # la finestra del Terminale resta aperta finché non si preme Invio
    if [ -t 0 ]; then read -r -p "Premi Invio per chiudere… " _; fi
}
trap finish EXIT

step() { echo; echo "▶ $*"; }
die() { echo "Errore: $*" >&2; exit 1; }

# --- controlli ---------------------------------------------------------------------------

command -v git >/dev/null || die "git non trovato."
command -v gh >/dev/null || die "GitHub CLI non trovata: installala con 'brew install gh'."
gh auth status >/dev/null 2>&1 || die "non sei collegato a GitHub: esegui 'gh auth login'."

STUDIO_JDK="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -d "$STUDIO_JDK" ]; then export JAVA_HOME="$STUDIO_JDK"; fi
[ -n "${JAVA_HOME:-}" ] || die "JDK non trovato: installa Android Studio o imposta JAVA_HOME."

if [ -n "$(git status --porcelain)" ]; then
    echo "Ci sono modifiche non ancora committate:"
    git status --short
    if [ -t 0 ]; then
        read -r -p "Le includo in questa release? [s/N] " answer
    else
        answer="n"
    fi
    case "$answer" in
        s | S | si | Si | SI | sì | Sì)
            git add -A
            git commit -q -m "Modifiche prima della release"
            ;;
        *) die "committa o scarta le modifiche, poi riprova." ;;
    esac
fi

# --- versione ----------------------------------------------------------------------------

read_prop() { grep "^$1=" version.properties | cut -d= -f2; }
CODE=$(read_prop versionCode)
NAME=$(read_prop versionName)

if gh release view "v$NAME" >/dev/null 2>&1; then
    # 1.0.4 -> 1.0.5
    NAME="${NAME%.*}.$((${NAME##*.} + 1))"
    CODE=$((CODE + 1))
    printf '# Aggiornato automaticamente da release.command\nversionCode=%s\nversionName=%s\n' "$CODE" "$NAME" >version.properties
fi
step "Versione $NAME (build $CODE)"

# --- firma -------------------------------------------------------------------------------

if [ ! -f keystore.properties ]; then
    step "Prima pubblicazione: creo la chiave di firma dell'app"
    PASSWORD=$(openssl rand -hex 16)
    "$JAVA_HOME/bin/keytool" -genkeypair -keystore release.keystore -alias ondalunga \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$PASSWORD" -keypass "$PASSWORD" -dname "CN=Onda Lunga" >/dev/null 2>&1
    (
        umask 077
        printf 'storeFile=release.keystore\nstorePassword=%s\nkeyAlias=ondalunga\nkeyPassword=%s\n' "$PASSWORD" "$PASSWORD" >keystore.properties
    )
    echo "  Creati release.keystore e keystore.properties: FANNE UNA COPIA AL SICURO."
    echo "  Senza quella chiave non potrai più pubblicare aggiornamenti installabili sopra i precedenti."
fi

# --- compilazione ------------------------------------------------------------------------

step "Compilo l'APK"
if ! ./gradlew --quiet testDebugUnitTest assembleRelease; then
    git checkout -- version.properties
    die "compilazione fallita: versione ripristinata."
fi

APK="build/OndaLunga-$NAME.apk"
mkdir -p build
cp app/build/outputs/apk/release/app-release.apk "$APK"
echo "  $APK ($(du -h "$APK" | cut -f1 | tr -d ' '))"

# --- pubblicazione -----------------------------------------------------------------------

step "Aggiorno il repository"
if ! git diff --quiet -- version.properties; then
    git add version.properties
    git commit -q -m "Release v$NAME"
fi
git push -q origin HEAD

step "Pubblico la release v$NAME"
gh release create "v$NAME" "$APK" \
    --title "Onda Lunga $NAME" \
    --notes "Scarica **OndaLunga-$NAME.apk** sul telefono Android e aprilo per installare il gioco (serve Android 8.0 o successivo)." \
    --latest

step "Rimuovo le release precedenti"
gh release list --limit 100 --json tagName --jq '.[].tagName' | while read -r tag; do
    if [ -n "$tag" ] && [ "$tag" != "v$NAME" ]; then
        gh release delete "$tag" --yes --cleanup-tag
        echo "  rimossa $tag"
    fi
done

echo
gh release view "v$NAME" --json url --jq '.url'
