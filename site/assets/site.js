(() => {
  "use strict";
  const owner = "a1070503737-dev";
  const repo = "QingZhang";
  const base = "https://github.com/" + owner + "/" + repo;
  const latestReleasePage = base + "/releases/latest";
  const downloadLinks = ["download-apk", "download-apk-bottom"].map((id) => document.getElementById(id));
  const label = document.getElementById("release-status");
  const bytesToSize = (n) => (n / 1048576).toFixed(1) + " MB";

  async function latestApk() {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);
    try {
      const response = await fetch("https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest", {
        headers: { Accept: "application/vnd.github+json" },
        cache: "no-store",
        signal: controller.signal,
      });
      if (!response.ok) throw new Error("Release unavailable");
      const release = await response.json();
      const tag = release.tag_name;
      if (release.draft || release.prerelease || !/^v\d+\.\d+\.\d+$/.test(tag) || !Array.isArray(release.assets)) {
        throw new Error("Invalid stable release");
      }
      const version = tag.slice(1);
      const name = "renovation-ledger-" + version + "-signed.apk";
      const asset = release.assets.find((item) => item.name === name && item.state === "uploaded");
      const url = base + "/releases/download/" + tag + "/" + name;
      if (asset?.browser_download_url !== url) throw new Error("Verified APK unavailable");
      return { tag, url, size: asset.size };
    } finally {
      clearTimeout(timeout);
    }
  }

  for (const link of downloadLinks) {
    link.addEventListener("click", async (event) => {
      if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      event.preventDefault();
      label.textContent = "正在获取最新安装包…";
      let url = latestReleasePage;
      try {
        const apk = await latestApk();
        url = apk.url;
        label.textContent = "最新正式版 " + apk.tag + " · " + bytesToSize(apk.size) + " · 免费下载";
      } catch {
        label.textContent = "正在打开 GitHub 最新发行版…";
      }
      window.location.assign(url);
    });
  }

  latestApk().then(({ tag, size }) => {
    label.textContent = "最新正式版 " + tag + " · " + bytesToSize(size) + " · 免费下载";
  }).catch(() => {
    label.textContent = "可在 GitHub 最新发行版页面下载";
  });
})();
