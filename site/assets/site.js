(() => {
  "use strict";
  const label = document.getElementById("release-status");
  fetch("https://api.github.com/repos/a1070503737-dev/QingZhang/releases/latest", {
    headers: { Accept: "application/vnd.github+json" },
    cache: "no-store",
  }).then((response) => {
    if (response.ok) return response.json();
  }).then((release) => {
    if (!release || release.draft || release.prerelease || !/^v\d+\.\d+\.\d+$/.test(release.tag_name)) return;
    const apk = release.assets?.find((asset) =>
      asset.name === "renovation-ledger-latest.apk" && asset.state === "uploaded"
    );
    if (apk) {
      const size = (apk.size / 1048576).toFixed(1) + " MB";
      label.textContent = "最新正式版 " + release.tag_name + " · " + size + " · 免费下载";
    }
  }).catch(() => {
    // Download buttons use a stable link and do not rely on the API.
  });
})();
