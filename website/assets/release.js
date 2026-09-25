/* One place to update per release. Elements with data-release="key" (or "beta.key")
   get the value as text, or as href when the element is a link. */
window.DIH_RELEASE = {
  version: "5.0",
  mc: "26.2",
  file: "DIH-Client-5.0-26.2.jar",
  size: "20.8 MB",
  sha256: "2c2e62f56554bfcde706104dc30a72cd6d362fcdb5a640b7ee3e6cd53a70b148",
  repo: "https://github.com/ahigherdesire/dih-client",
  download: "https://github.com/ahigherdesire/dih-client/releases/download/v5.0/DIH-Client-5.0-26.2.jar",
  /* JourneyMap can't be bundled (All Rights Reserved), so link Modrinth's own file. */
  jmFile: "journeymap-fabric-26.2-6.0.9.jar",
  journeymap: "https://cdn.modrinth.com/data/lfHFW1mp/versions/tbOrSD59/journeymap-fabric-26.2-6.0.9.jar",
  /* Pre-release: a GitHub pre-release, so the in-game update checker (releases/latest) ignores it. */
  beta: {
    label: "5.1 beta",
    version: "5.1-beta.2",
    file: "DIH-Client-5.1-beta.2-26.2.jar",
    size: "20.8 MB",
    sha256: "79fc91fcf2beef9c0567b30f84773c86619ef10c2bd307bbcb22107cab57ef99",
    download: "https://github.com/ahigherdesire/dih-client/releases/download/v5.1-beta.2/DIH-Client-5.1-beta.2-26.2.jar",
    notes: "https://github.com/ahigherdesire/dih-client/releases/tag/v5.1-beta.2"
  }
};
