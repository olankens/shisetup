package com.example.shisetup.adbdroid

enum class ShieldResolution(val content: String, val payload: List<Any>) {
    P2160_DOLBY_HZ23("4K 23.976 Hz Dolby Vision", listOf("4K", "23.976", true)),
    P2160_DOLBY_HZ59("4K 59.940 Hz Dolby Vision", listOf("4K", "59.940", true)),
    P2160_HDR10_HZ23("4K 23.976 Hz HDR10", listOf("4K", "23.976", false)),
    P2160_HDR10_HZ59("4K 59.940 Hz HDR10", listOf("4K", "59.940", false)),
    P1080_DOLBY_HZ23("1080p 23.976 Hz Dolby Vision", listOf("1080", "23.976", true)),
    P1080_DOLBY_HZ59("1080p 59.940 Hz Dolby Vision", listOf("1080", "59.940", true)),
    P1080_HDR10_HZ23("1080p 23.976 Hz HDR10", listOf("1080", "23.976", false)),
    P1080_HDR10_HZ59("1080p 59.940 Hz HDR10", listOf("1080", "59.940", false));
}