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
    @field:FieldDescription("是否显示史莱姆爆炸倒计时")
    @JvmField var showExplosionCountdown = true
    @field:FieldDescription("每轮爆炸时间随机增减的最大秒数")
    @JvmField var explosionTimeVarianceSeconds = 0
    @field:FieldDescription("史莱姆追踪速度，每tick移动的格数")
    @JvmField var slimeSpeed = 0.18
    @field:FieldDescription("史莱姆附身距离")
    @JvmField var attachDistance = 1.6
    @field:FieldDescription("附身后允许投掷的秒数")
    @JvmField var throwDelaySeconds = 2
    @field:FieldDescription("持有史莱姆的爆炸时限秒数，按累计开关计时，小于等于0时禁用")
    @JvmField var attachedExplosionSeconds = 0
    @field:FieldDescription("是否按玩家累计本局持有史莱姆的时间，关闭后每次附身重新计时")
    @JvmField var cumulativeAttachedExplosion = true
    @field:FieldDescription("投掷速度")
    @JvmField var throwSpeed = 1.1
    @field:FieldDescription("玩家初始行走速度（Bukkit速度值，默认值为0.2）")
    @JvmField var playerSpeed = 0.12f
    @field:FieldDescription("史莱姆附身时的行走速度")
    @JvmField var attachedPlayerSpeed = 0.08f
}
