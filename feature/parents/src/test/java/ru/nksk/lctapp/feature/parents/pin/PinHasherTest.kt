package ru.nksk.lctapp.feature.parents.pin

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    private val hasher = PinHasher()

    @Test fun `same PIN receives independent salted records`() {
        val first = hasher.create("1234")
        val second = hasher.create("1234")
        assertNotEquals(first, second)
        assertTrue(hasher.verify("1234", first))
        assertTrue(hasher.verify("1234", second))
        assertFalse(hasher.verify("1235", first))
    }

    @Test(expected = IOException::class)
    fun `unsupported record versions are storage errors`() {
        hasher.validate("2|pbkdf2-sha1|210000|${"01".repeat(16)}|${"02".repeat(32)}")
    }

    @Test(expected = IOException::class)
    fun `unbounded work factor cannot be loaded`() {
        hasher.validate("1|pbkdf2-sha1|2147483647|${"01".repeat(16)}|${"02".repeat(32)}")
    }
}
