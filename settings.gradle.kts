enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "jellyfin-androidtv"

// Application
include(":app")

// Modules
include(":design")
include(":playback:core")
include(":playback:jellyfin")
include(":playback:media3:exoplayer")
include(":playback:media3:session")
include(":playback:vlc")
include(":preference")

pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
		google()
	}
}

dependencyResolutionManagement {
	repositories {
		mavenCentral()
		google()

		// Jellyfin SDK
		mavenLocal {
			content {
				includeVersionByRegex("org.jellyfin.sdk", ".*", "latest-SNAPSHOT")
			}
		}
		maven("https://s01.oss.sonatype.org/content/repositories/snapshots/") {
			content {
				includeVersionByRegex("org.jellyfin.sdk", ".*", "master-SNAPSHOT")
				includeVersionByRegex("org.jellyfin.sdk", ".*", "openapi-unstable-SNAPSHOT")
			}
		}

		// NewPipeExtractor, which resolves youtube trailer urls to playable streams, publishes
		// through JitPack only. Restricted to that one group so nothing else can be pulled from a
		// build-on-demand repository.
		maven("https://jitpack.io") {
			content {
				// The extractor is a multi-module build, so its own submodules resolve under
				// com.github.TeamNewPipe.NewPipeExtractor rather than the plain group.
				includeGroupByRegex("com\\.github\\.TeamNewPipe.*")
			}
		}
	}
}
