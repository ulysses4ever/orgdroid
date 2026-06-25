package dev.orgdroid.org

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagTest {
    @Test fun simpleAlphanumericIsValid() { assertTrue(isValidTag("work")) }
    @Test fun digitsAllowed() { assertTrue(isValidTag("p1")) }
    @Test fun underscoreAtPercentAllowed() {
        assertTrue(isValidTag("home_office"))
        assertTrue(isValidTag("@meeting"))
        assertTrue(isValidTag("50%done"))
    }
    @Test fun emptyIsInvalid() { assertFalse(isValidTag("")) }
    @Test fun spaceIsInvalid() { assertFalse(isValidTag("two words")) }
    @Test fun colonIsInvalid() { assertFalse(isValidTag("a:b")) }
    @Test fun hashIsInvalid() { assertFalse(isValidTag("foo#bar")) }
}
