package io.github.foxesrcool1.margin.core.eink

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class EinkThreadTest {

    @Test
    fun `calls run one after the other, off the caller's thread, and a failure does not stop the next`() {
        val seen = mutableListOf<String>()
        val callers = mutableSetOf<Thread>()
        val done = CountDownLatch(3)

        EinkThread.run { callers += Thread.currentThread(); seen += "start"; done.countDown() }
        EinkThread.run { done.countDown(); throw IllegalStateException("vendor crashed") }
        EinkThread.run { seen += "stop"; done.countDown() }

        done.await(5, TimeUnit.SECONDS)
        assertEquals(listOf("start", "stop"), seen)
        assertEquals(false, callers.contains(Thread.currentThread()))
    }
}
