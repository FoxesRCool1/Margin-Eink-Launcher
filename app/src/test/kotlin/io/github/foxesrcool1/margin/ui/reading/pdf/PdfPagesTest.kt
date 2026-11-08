package io.github.foxesrcool1.margin.ui.reading.pdf

import java.io.File
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The platform PdfRenderer has no real engine under Robolectric, so only the
 * path that needs none is tested here: a file that is not there. A damaged
 * page (pageBox returning null) can only be tried on the tablet.
 */
@RunWith(RobolectricTestRunner::class)
class PdfPagesTest {

    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `open of a missing file returns null without throwing`() {
        assertNull(PdfPages.open(File(temporary.root, "no such book.pdf")))
    }
}
