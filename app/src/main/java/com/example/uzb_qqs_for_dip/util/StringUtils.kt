package com.example.uzb_qqs_for_dip.util

fun String.toInitials(): String {
    val parts = this.trim().split("\\s+".toRegex())
    if (parts.isEmpty() || parts[0].isEmpty()) return ""
    val surname = parts[0]
    if (parts.size == 1) return surname
    
    val initials = parts.drop(1)
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { "${it.first().uppercaseChar()}." }
        
    return if (initials.isNotEmpty()) "$surname $initials" else surname
}
