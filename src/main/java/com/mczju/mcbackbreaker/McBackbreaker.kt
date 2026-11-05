package com.mczju.mcbackbreaker

import com.github.mczjuops.mczjugamecore.MCZJUGameCore
import com.mczju.mcbackbreaker.core.BackbreakerGame
import com.mczju.mcbackbreaker.core.BackbreakerRoom
import com.yikuni.mc.reflect.PluginLoader
import org.bukkit.plugin.java.JavaPlugin

class McBackbreaker : JavaPlugin() {
    override fun onEnable() {
        instance = this
        PluginLoader.run(javaClass)
        MCZJUGameCore.getGameManager().registerGame(BackbreakerGame::class.java, BackbreakerRoom::class.java)
        com.mczju.mcbackbreaker.statistics.RoundStatistics.register(this)
        com.mczju.mcbackbreaker.achievement.GameAchievements.register(this)
    }

    override fun onDisable() { BackbreakerGame.shutdownAll() }

    companion object {
        lateinit var instance: McBackbreaker
            private set
    }
}
