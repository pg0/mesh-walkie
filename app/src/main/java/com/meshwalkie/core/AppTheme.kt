package com.meshwalkie.core

/**
 * Selectable UI theme. Persisted by Settings as a string (enum name). FIELD
 * is the default. Pure Kotlin, no Android/Compose imports, so it is usable
 * from the service package without pulling in UI dependencies.
 */
enum class AppTheme { FIELD, CORRUPTION, RADIO, DARK, NIGHT }
