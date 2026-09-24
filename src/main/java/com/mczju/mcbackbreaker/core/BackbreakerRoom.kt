package com.mczju.mcbackbreaker.core

import com.github.mczjuops.mczjugamecore.game.room.FieldDescription
import com.github.mczjuops.mczjugamecore.game.room.JsonGameRoom
import org.bukkit.Location

class BackbreakerRoom : JsonGameRoom() {
    @field:FieldDescription("玩家和史莱姆的出生点，必填")
    @JvmField var spawnLocation: Location? = null
    @field:FieldDescription("开局后史莱姆出现的等待秒数")
    @JvmField var spawnDelaySeconds = 5
    @field:FieldDescription("每轮倒计时秒数")
    @JvmField var roundSeconds = 30
    @field:FieldDescription("史莱姆追踪速度，每tick移动的格数")
    @JvmField var slimeSpeed = 0.18
    @field:FieldDescription("史莱姆附身距离")
    @JvmField var attachDistance = 1.6
    @field:FieldDescription("附身后允许投掷的秒数")
    @JvmField var throwDelaySeconds = 2
    @field:FieldDescription("投掷速度")
    @JvmField var throwSpeed = 1.1
    @field:FieldDescription("玩家初始缓慢等级（0表示缓慢I）")
    @JvmField var slowAmplifier = 3
    @field:FieldDescription("附身缓慢等级（0表示缓慢I）")
    @JvmField var attachedSlowAmplifier = 4
}
