"""Attach the verified official APK under the website's stable download name."""

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


DOWNLOAD_NAME = "renovation-ledger-latest.apk"


def latest_release(repo):
    return json.loads(subprocess.check_output(["gh", "api", f"repos/{repo}/releases/latest"]))


def download_already_exists(release, original):
    for asset in release["assets"]:
        if asset["name"] == DOWNLOAD_NAME:
            if (asset["state"], asset["size"], asset.get("digest")) != (
                "uploaded", original["size"], original["digest"],
            ):
                raise SystemExit("Existing website APK differs from the signed release APK")
            return True
    return False


def main():
    repo = os.environ["GITHUB_REPOSITORY"]
    release = latest_release(repo)
    tag = release["tag_name"]
    if release["draft"] or release["prerelease"] or not re.fullmatch(r"v\d+\.\d+\.\d+", tag):
        raise SystemExit("Expected a stable QingZhang release")

    package_name = f"renovation-ledger-{tag[1:]}-signed.apk"
    original = next((asset for asset in release["assets"] if asset["name"] == package_name), None)
    if not original or original["state"] != "uploaded" or not re.fullmatch(
        r"sha256:[a-f0-9]{64}", original.get("digest") or "",
    ):
        raise SystemExit("The latest release is missing its signed APK or SHA-256 digest")
    if download_already_exists(release, original):
        print(f"{tag}: website APK already matches the official APK")
        return

    with tempfile.TemporaryDirectory() as directory:
        subprocess.run([
            "gh", "release", "download", tag, "--repo", repo, "--dir", directory,
            "--pattern", package_name, "--pattern", "update.json",
        ], check=True)
        apk = Path(directory, package_name)
        manifest = json.loads(Path(directory, "update.json").read_text(encoding="utf-8"))
        with apk.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        expected_apk = {"fileName": package_name, "size": apk.stat().st_size, "sha256": digest}
        if (
            manifest.get("schemaVersion") != 1
            or manifest.get("packageName") != "com.zhuangxiu.ledger"
            or manifest.get("versionName") != tag[1:]
            or manifest.get("apk") != expected_apk
            or (original["size"], original["digest"]) != (apk.stat().st_size, f"sha256:{digest}")
        ):
            raise SystemExit("Signed APK, GitHub asset metadata and update manifest disagree")

        current = latest_release(repo)
        if current["id"] != release["id"]:
            print(f"{tag} was superseded while verifying the APK")
            return
        if download_already_exists(current, original):
            print(f"{tag}: website APK was attached during verification")
            return

        website_apk = apk.rename(Path(directory, DOWNLOAD_NAME))
        subprocess.run(["gh", "release", "upload", tag, str(website_apk), "--repo", repo], check=True)
        print(f"{tag}: attached the verified website APK")


if __name__ == "__main__":
    main()
