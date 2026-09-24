package com.mczju.mcbackbreaker.listener

import com.github.mczjuops.mczjugamecore.player.PlayerExt
import com.mczju.mcbackbreaker.core.BackbreakerGame
import com.yikuni.mc.reflect.annotation.YikuniEvent
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent

@YikuniEvent
class GameListener : Listener {
    @EventHandler(priority = EventPriority.HIGH)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND) return
        val game = PlayerExt(event.player).game as? BackbreakerGame ?: return
        if (game.throwSlime(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND) return
        if (!BackbreakerGame.isGameSlime(event.rightClicked)) return
        val game = PlayerExt(event.player).game as? BackbreakerGame ?: return
        if (game.throwSlime(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        if (BackbreakerGame.isGameSlime(event.entity)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPvP(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val attacker = when (val source = event.damager) {
            is Player -> source
            is Projectile -> source.shooter as? Player
            else -> null
        } ?: return
        if (PlayerExt(victim).game is BackbreakerGame || PlayerExt(attacker).game is BackbreakerGame) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        if (PlayerExt(event.player).game is BackbreakerGame) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        if (PlayerExt(event.player).game is BackbreakerGame) event.isCancelled = true
    }
}
