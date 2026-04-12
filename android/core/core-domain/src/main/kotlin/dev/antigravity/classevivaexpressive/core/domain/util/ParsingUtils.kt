package dev.antigravity.classevivaexpressive.core.domain.util

/**
 * Robust decimal parser that handles both dots and commas.
 * This is particularly useful for Italian locale where ',' is the standard separator,
 * but API or developer inputs might use '.'.
 */
fun String.parseDecimal(): Double? {
    if (this.isBlank()) return null
    val cleaned = this.trim()
    
    // 1. Try standard toDoubleOrNull (handles "12.34")
    cleaned.toDoubleOrNull()?.let { return it }
    
    // 2. Try replacing comma with dot (handles "12,34")
    val withDot = cleaned.replace(',', '.')
    withDot.toDoubleOrNull()?.let { return it }
    
    return null
}
