package com.shuttermute.privilege

data class AdbEndpoint(
    val host: String,
    val port: Int,
    val pairing: Boolean,
)
