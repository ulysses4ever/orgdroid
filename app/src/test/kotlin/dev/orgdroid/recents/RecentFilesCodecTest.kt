package dev.orgdroid.recents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentFilesCodecTest {

    @Test fun emptyListEncodesToEmptyString() {
        assertEquals("", RecentFilesCodec.encode(emptyList()))
    }

    @Test fun emptyStringDecodesToEmptyList() {
        assertTrue(RecentFilesCodec.decode("").isEmpty())
    }

    @Test fun singleEntryRoundTrips() {
        val item = RecentFile(
            uri = "content://com.example/doc/123",
            displayName = "test.org",
            lastOpenedAt = 1_700_000_000_000L,
        )
        val decoded = RecentFilesCodec.decode(RecentFilesCodec.encode(listOf(item)))
        assertEquals(listOf(item), decoded)
    }

    @Test fun multipleEntriesPreserveOrder() {
        val a = RecentFile("uri-a", "a.org", 1L)
        val b = RecentFile("uri-b", "b.org", 2L)
        val c = RecentFile("uri-c", "c.org", 3L)
        val items = listOf(a, b, c)
        assertEquals(items, RecentFilesCodec.decode(RecentFilesCodec.encode(items)))
    }

    @Test fun displayNameWithSpecialCharsRoundTrips() {
        val item = RecentFile(
            uri = "u",
            displayName = "weird\tname\nwith\\escapes.org",
            lastOpenedAt = 42L,
        )
        assertEquals(item, RecentFilesCodec.decode(RecentFilesCodec.encode(listOf(item))).single())
    }

    @Test fun malformedLinesAreSkipped() {
        val good = "1700000000000\tone.org\turi-1\n"
        val tooFewFields = "nope\n"
        val nonNumericTs = "abc\tname\turi\n"
        val decoded = RecentFilesCodec.decode(good + tooFewFields + nonNumericTs)
        assertEquals(1, decoded.size)
        assertEquals("one.org", decoded[0].displayName)
    }

    @Test fun blankLinesAreSkipped() {
        val text = "1\ta\turi-1\n\n2\tb\turi-2\n"
        assertEquals(2, RecentFilesCodec.decode(text).size)
    }
}
