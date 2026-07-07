package com.projectapex.core.model

enum class SessionStatus {
    OFFLINE,
    LIVE,
    REPLAY
}

data class SessionState(
    val status: SessionStatus = SessionStatus.OFFLINE,
    val eventName: String = "British Grand Prix"
)
