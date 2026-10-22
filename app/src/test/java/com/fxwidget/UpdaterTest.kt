package com.fxwidget

import org.junit.Assert.*
import org.junit.Test

class UpdaterTest {
    @Test fun parsesRelease() {
        val r = Updater.parseRelease(
            """{"tag_name":"v1.1.9","body":"Fix","assets":[
              {"name":"notes.txt","browser_download_url":"x"},
              {"name":"fx-widget-v1.1.9.apk","browser_download_url":"https://e/x.apk"}]}"""
        )!!
        assertEquals("1.1.9", r.version)
        assertEquals(9L, r.build)
        assertEquals("https://e/x.apk", r.apkUrl)
    }

    @Test fun noApkMeansNoRelease() =
        assertNull(Updater.parseRelease("""{"tag_name":"v1.1.9","assets":[]}"""))
}
