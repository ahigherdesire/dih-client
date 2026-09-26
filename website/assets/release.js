/* One place to update per release. Elements with data-release="key" (or "beta.key")
   get the value as text, or as href when the element is a link. */
window.DIH_RELEASE = {
  version: "5.0.1",
  mc: "26.2",
  file: "DIH-Client-5.0.1-26.2.jar",
  size: "20.8 MB",
  sha256: "7a10cdb8168856611e9d5f255f4e6331e7de3e63108c348a0da5c50d56fbd2a2",
  repo: "https://github.com/ahigherdesire/dih-client",
  download: "https://github.com/ahigherdesire/dih-client/releases/download/v5.0.1/DIH-Client-5.0.1-26.2.jar",
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
  },
  beta3: {
    version: "5.1-beta.3",
    mc: "26.2",
    file: "DIH-Client-5.1-beta.3-26.2.jar",
    size: "20.8 MB",
    sha256: "e553d76b6a285250027f00de8cbd3bf222c08188ea7e47834ccd5b9ccbea7f45",
    download: "https://github.com/ahigherdesire/dih-client/releases/download/v5.1-beta.3/DIH-Client-5.1-beta.3-26.2.jar",
    notes: "https://github.com/ahigherdesire/dih-client/releases/tag/v5.1-beta.3"
  },
  /* The same beta built for Minecraft 26.3 (an extra asset on the beta 3 release). */
  beta3_263: {
    version: "5.1-beta.3",
    mc: "26.3",
    file: "DIH-Client-5.1-beta.3-26.3.jar",
    size: "21.1 MB",
    sha256: "daf5f6624d697f9934479f5e8809c2bf6957dd94cd034edf17410dc05767e5f8",
    download: "https://github.com/ahigherdesire/dih-client/releases/download/v5.1-beta.3/DIH-Client-5.1-beta.3-26.3.jar",
    notes: "https://github.com/ahigherdesire/dih-client/releases/tag/v5.1-beta.3"
  }
};
