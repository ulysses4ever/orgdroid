package dev.orgdroid.org

/**
 * Org tag character class: `[A-Za-z0-9_@%]+`. Mirrors the heading-line regex
 * used by [OrgParser], minus the `:` and `#` separators which are structural.
 * A tag is valid iff it is non-empty and every character is in the class.
 */
fun isValidTag(s: String): Boolean =
    s.isNotEmpty() && s.all { it.isLetterOrDigit() || it == '_' || it == '@' || it == '%' }
