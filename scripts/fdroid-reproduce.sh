#!/usr/bin/env bash
# Rebuild a release the way F-Droid does and check it against our signed APK.
#
# Runs INSIDE registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie, the image fdroiddata's
# own CI builds merge requests in (it is provisioned like the production buildserver). Setup
# mirrors that job's "fdroid build" script: fdroidserver from git master on PATH, JDK 21, the
# gradlew-fdroid shim. The recipe is the LIVE one from fdroiddata master, so anything F-Droid's
# maintainers add to it is exercised here too.
#
# `fdroid build` itself downloads the `Binaries:` APK, compares it with its own unsigned build
# (verify_apks: everything but the signature must match) and checks the signer against
# `AllowedAPKSigningKeys`. A mismatch is a failed build, exactly as on F-Droid. The release
# must therefore be PUBLISHED: a draft's assets 404 and would read as a reproducibility failure.
#
# A brand-new tag has no `Builds:` entry until F-Droid's checkupdates bot adds one, days
# later. Like that bot, this copies the last entry and points it at the tag's commit.
#
#   docker run --rm -v "$PWD/scripts:/scripts:ro" -v "$PWD/fdroid-out:/out" \
#     registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie \
#     bash /scripts/fdroid-reproduce.sh v2026.10.8
#
# amd64 only; under emulation on Apple Silicon see docs/fdroid/MERGE_REQUEST.md "Rig notes".

set -euo pipefail

TAG="${1:?usage: fdroid-reproduce.sh <tag>}"
APP_ID=com.garfiec.librechat
REPO=garfiec/Librechat-Mobile
OUT=/out
mkdir -p "$OUT"

# shellcheck disable=SC1091
source /etc/profile.d/bsenv.sh
export PYTHONUNBUFFERED=true

apt-get update -q
apt-get install -qy --no-install-recommends openjdk-21-jdk-headless curl git python3-yaml >/dev/null
update-alternatives --set java /usr/lib/jvm/java-21-openjdk-amd64/bin/java

if [ -d "$home_vagrant/gradlew-fdroid/.git" ]; then
  git -C "$home_vagrant/gradlew-fdroid" pull --ff-only -q
fi

rm -rf "$fdroidserver"
git clone -q --depth 1 https://gitlab.com/fdroid/fdroidserver.git "$fdroidserver"
echo "fdroidserver $(git -C "$fdroidserver" rev-parse HEAD)"
export PATH="$fdroidserver:$PATH"
export PYTHONPATH="$fdroidserver:$fdroidserver/examples"

# Annotated tags: ^{} peels to the commit, which is what a recipe's `commit:` must name.
commit="$(git ls-remote "https://github.com/$REPO.git" "refs/tags/$TAG^{}" | cut -f1)"
if [ -z "$commit" ]; then
  echo "::error::tag $TAG not found in $REPO"
  exit 1
fi
props="$(curl -fsSL "https://raw.githubusercontent.com/$REPO/$commit/version.properties")"
version_name="$(printf '%s\n' "$props" | sed -n 's/^versionName=//p')"
version_code="$(printf '%s\n' "$props" | sed -n 's/^versionCode=//p')"
echo "$TAG -> $commit, $version_name ($version_code)"

data=/work/fdroiddata
mkdir -p "$data/metadata"
cd "$data"
curl -fsSL "https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/$APP_ID.yml" \
  -o "metadata/$APP_ID.yml"

python3 - "metadata/$APP_ID.yml" "$version_name" "$version_code" "$commit" <<'PY'
import copy, sys
import yaml

path, name, code, commit = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
with open(path) as f:
    recipe = yaml.safe_load(f)
builds = recipe["Builds"]
existing = [b for b in builds if int(b["versionCode"]) == code]
if existing:
    if existing[0]["commit"] != commit:
        sys.exit("recipe builds %s from %s, but the tag is %s" % (code, existing[0]["commit"], commit))
    print("recipe already has %s" % code)
else:
    block = copy.deepcopy(builds[-1])
    block.update(versionName=name, versionCode=code, commit=commit)
    builds.append(block)
    print("added a Builds entry for %s, copied from %s" % (code, builds[-2]["versionCode"]))
with open(path, "w") as f:
    yaml.safe_dump(recipe, f, sort_keys=False)
PY
cp "metadata/$APP_ID.yml" "$OUT/recipe.yml"

gradle="$(command -v gradlew-fdroid || true)"
cat > config.yml <<EOF
sdk_path: $ANDROID_HOME
${gradle:+gradle: $gradle}
EOF
chmod 0600 config.yml
echo "gradle shim: ${gradle:-fdroidserver default}"

set +e
fdroid build --verbose --test --stop --no-tarball --refresh-scanner "$APP_ID:$version_code" 2>&1 \
  | tee "$OUT/fdroid-build.log"
status=${PIPESTATUS[0]}
set -e
cp -r logs "$OUT/" 2>/dev/null || true

if [ "$status" -ne 0 ] || ! grep -q "compared built binary to supplied reference binary successfully" "$OUT/fdroid-build.log"; then
  echo "::error title=F-Droid rebuild did not reproduce::$TAG ($version_code) failed to build or verify in F-Droid's image; see the fdroid-build.log artifact."
  exit 1
fi
echo "$TAG reproduces in F-Droid's build environment."
