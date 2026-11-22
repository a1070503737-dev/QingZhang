// The HTML includes a verified static APK fallback. Refresh the download link
// from GitHub's public release API when it is available, without storing credentials.
(() => {
  "use strict";
  const owner = "a1070503737-dev";
  const repo = "code";
  const fallbackVersion = "1.3.23";
  const base = "https://github.com/" + owner + "/" + repo;
  const mainLink = document.getElementById("download-apk");
  const secondLink = document.getElementById("download-apk-bottom");
  const label = document.getElementById("release-status");
  const bytesToSize = (n) => (n / 1048576).toFixed(1) + " MB";
  const toVersion = (tag) => /^v\d+\.\d+\.\d+$/.test(tag) ? tag.slice(1) : null;
  fetch("https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest", {
    headers: { Accept: "application/vnd.github+json" },
    cache: "no-store"
  })
    .then((r) => { if (!r.ok) throw new Error("release unavailable"); return r.json(); })
    .then((release) => {
      if (release.draft || release.prerelease) throw new Error("not a formal release");
      const version = toVersion(release.tag_name);
      if (!version || !Array.isArray(release.assets)) throw new Error("invalid release");
      const name = "renovation-ledger-" + version + "-signed.apk";
      const asset = release.assets.find((item) => item.name === name && item.state === "uploaded");
      if (!asset) throw new Error("APK not found");
      const expected = base + "/releases/download/" + release.tag_name + "/" + name;
      if (asset.browser_download_url !== expected) throw new Error("unexpected URL");
      mainLink.href = expected;
      secondLink.href = expected;
      label.textContent = "最新正式版 v" + version + " · " + bytesToSize(asset.size) + " · 免费下载";
    })
    .catch(() => {
      // A working fallback is preferable to a broken button when GitHub API is rate-limited.
      label.textContent = "预设正式版 v" + fallbackVersion + " · 如需核对最新版本，请打开「查看发行版」";
    });
})();
