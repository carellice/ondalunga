package com.flaviocecca.ondalunga.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesTest {
    @Test
    fun versionsCompareNumberByNumber() {
        assertTrue(Releases.isNewer("1.0.2", "1.0.1"))
        assertTrue(Releases.isNewer("1.0.10", "1.0.9"))
        assertTrue(Releases.isNewer("1.1", "1.0.9"))
        assertTrue(Releases.isNewer("2.0.0", "1.9.9"))
        assertFalse(Releases.isNewer("1.0.1", "1.0.1"))
        assertFalse(Releases.isNewer("1.0.0", "1.0.1"))
        assertFalse(Releases.isNewer("1.0", "1.0.0"))
    }

    @Test
    fun latestReleaseIsReadFromGitHubAnswer() {
        val json = """
            {"tag_name": "v1.0.2", "assets": [
              {"name": "notes.txt", "size": 10, "browser_download_url": "https://github.com/carellice/ondalunga/releases/download/v1.0.2/notes.txt"},
              {"name": "OndaLunga-1.0.2.apk", "size": 1652161, "browser_download_url": "https://github.com/carellice/ondalunga/releases/download/v1.0.2/OndaLunga-1.0.2.apk"}
            ]}
        """
        assertEquals(
            ReleaseInfo("1.0.2", "https://github.com/carellice/ondalunga/releases/download/v1.0.2/OndaLunga-1.0.2.apk", 1652161),
            Releases.parse(json),
        )
    }

    @Test
    fun apkHostedElsewhereIsIgnored() {
        val json = """
            {"tag_name": "v9.9.9", "assets": [
              {"name": "OndaLunga.apk", "size": 5, "browser_download_url": "https://example.com/OndaLunga.apk"}
            ]}
        """
        assertNull(Releases.parse(json))
        assertNull(Releases.parse("""{"tag_name": "v1.0.3", "assets": []}"""))
    }
}
