package com.projectapex.domain.model

data class CarState(
    val driver: Driver,
    val position: Int,
    val lap: Int,
    val gapToLeaderSeconds: Double,
    val tyreCompound: TyreCompound,
    val tyreAgeLaps: Int,
    val isInPitLane: Boolean
)
