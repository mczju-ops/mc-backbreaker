package com.mczju.mcbackbreaker.core

import com.github.mczjuops.mczjugamecore.MCZJUGameCore
import com.github.mczjuops.mczjugamecore.game.AbstractGame
import com.github.mczjuops.mczjugamecore.game.GameMeta
import com.github.mczjuops.mczjugamecore.game.GameState
import com.github.mczjuops.mczjugamecore.game.strategy.wait.DefaultGameWaitStrategy
import com.github.mczjuops.mczjugamecore.game.strategy.wait.GameWaitStrategy
import com.github.mczjuops.mczjugamecore.player.PlayerExt
import com.github.mczjuops.mczjugamecore.player.strategy.AbstractPlayerDeathStrategy
import com.github.mczjuops.mczjugamecore.player.strategy.AbstractPlayerQuitStrategy
import com.github.mczjuops.mczjugamecore.player.strategy.PlayerQuitReason
import com.mczju.mcbackbreaker.McBackbreaker
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.entity.BlockDisplay
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import org.joml.Matrix4f
import java.util.UUID
import kotlin.math.ceil

class BackbreakerGame : AbstractGame() {
    private enum class Phase { SPAWN_DELAY, HUNTING, ATTACHING, ATTACHED, THROWN, RESET_DELAY }
    private var phase = Phase.SPAWN_DELAY
    private var task: BukkitTask? = null
    private var bossBar: BossBar? = null
    private var slime: BlockDisplay? = null
    private var navigator: SlimeNavigator? = null
    private var thrownVelocity: Vector? = null
    private val alive = linkedSetOf<UUID>()
    private val survivors = linkedMapOf<UUID, String>()
    private val survivalTicks = mutableMapOf<UUID, Long>()
    private var holder: UUID? = null
    private var ticks = 0L
    private var phaseTicks = 0
    private var roundTicks = 0
    private var round = 0
    private var lethal = false
    private var ending = false
    private var flightStart: Location? = null

    override fun getId() = "mc_backbreaker_slime_trap"
    override fun getGameMeta() = GameMeta("史莱姆陷阱(制作中)", Material.SLIME_BLOCK, "Yikuni", listOf(
        "躲开邪恶史莱姆", "利用好友当挡箭牌", "最后存活的人获胜", "推荐人数：3-7"
    ))
    override fun getGameWaitStrategy(): GameWaitStrategy = DefaultGameWaitStrategy(this, 7, 2)

    override fun onGameInit(): Boolean {
        val r = gameRoom as BackbreakerRoom
        val spawn = r.spawnLocation
        val valid = spawn?.world != null && spawn.clone().subtract(0.0, 1.0, 0.0).block.type.isSolid &&
            spawn.block.isPassable && spawn.clone().add(0.0, 1.0, 0.0).block.isPassable &&
            r.spawnDelaySeconds in 0..60 && r.roundSeconds in 1..3600 &&
            r.slimeSpeed in 0.02..0.8 && r.attachDistance in 0.7..4.0 &&
            r.throwDelaySeconds in 0..60 && r.throwSpeed in 0.2..3.0 &&
            r.slowAmplifier in 0..10 && r.attachedSlowAmplifier in r.slowAmplifier..20
        if (!valid) logger.error("房间${r.roomName}配置无效：请设置出生点并检查史莱姆速度、距离和各时间参数")
        return valid
    }

    override fun onGameStart() {
        val r = gameRoom as BackbreakerRoom
        alive.clear()
        players.forEach { ext ->
            val player = ext.player
            alive.add(ext.uniqueId)
            survivors[ext.uniqueId] = player.name
            ext.resetState()
            player.inventory.clear()
            player.gameMode = GameMode.ADVENTURE
            player.teleport(r.spawnLocation!!)
            applyEffects(player, false)
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.3f)
            ext.sender().primary("躲开发光史莱姆！被附身一段时间后，左键可将它扔出。倒计时结束时，附身者会爆炸淘汰；若它暂未附身，下次附身者会立刻淘汰。")
        }
        navigator = SlimeNavigator(r)
        bossBar = Bukkit.createBossBar("史莱姆即将出现", BarColor.GREEN, BarStyle.SOLID).also { bar ->
            players.forEach { bar.addPlayer(it.player) }
        }
        phase = Phase.SPAWN_DELAY
        phaseTicks = r.spawnDelaySeconds * 20
        active.add(this)
        task = Bukkit.getScheduler().runTaskTimer(McBackbreaker.instance, Runnable { tick() }, 1L, 1L)
    }

    override fun onGameCancel() = cleanup(false)
    override fun onGameAbort() = cleanup(false)
    override fun onGameEnd() {
        announceRanking()
        cleanup(true)
    }
    override fun getPlayerDeathStrategy(): AbstractPlayerDeathStrategy = DeathStrategy(this)
    override fun getPlayerQuitStrategy(): AbstractPlayerQuitStrategy = QuitStrategy(this)

    fun isAlive(player: Player) = player.uniqueId in alive

    fun throwSlime(player: Player): Boolean {
        val r = gameRoom as BackbreakerRoom
        if (state != GameState.RUNNING || phase != Phase.ATTACHED || holder != player.uniqueId) return false
        if (phaseTicks < r.throwDelaySeconds * 20) {
            PlayerExt(player).actionBarSender().warn("再等${ceil((r.throwDelaySeconds * 20 - phaseTicks) / 20.0).toInt()}秒才能扔出")
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f)
            return true
        }
        if (slime == null) return true
        holder = null
        applyEffects(player, false)
        moveSlime(player.eyeLocation.clone().add(player.location.direction.clone().multiply(0.8)))
        thrownVelocity = player.eyeLocation.direction.clone().normalize().multiply(r.throwSpeed).add(Vector(0.0, 0.25, 0.0))
        phase = Phase.THROWN
        phaseTicks = 0
        player.world.playSound(player.location, Sound.ENTITY_SLIME_JUMP, 1.5f, 1.5f)
        player.world.spawnParticle(Particle.ITEM_SLIME, player.eyeLocation, 15, 0.3, 0.3, 0.3, 0.1)
        return true
    }

    fun eliminate(ext: PlayerExt, quit: Boolean = false) {
        if (state != GameState.RUNNING || !alive.remove(ext.uniqueId)) return
        survivalTicks[ext.uniqueId] = ticks
        val player = ext.player
        if (holder == ext.uniqueId) holder = null
        sender.warn("${ext.displayName}${if (quit) "退出游戏" else "被史莱姆淘汰"}！")
        if (quit && player.isOnline) {
            ext.resetState()
            player.gameMode = GameMode.ADVENTURE
            bossBar?.removePlayer(player)
        }
        if (!quit && player.isOnline) {
            player.world.spawnParticle(Particle.EXPLOSION, player.location.clone().add(0.0, 1.0, 0.0), 1)
            player.world.playSound(player.location, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.1f)
            ext.resetState()
            player.gameMode = GameMode.SPECTATOR
        }
        if (alive.size <= 1) {
            finish()
        } else if (phase != Phase.RESET_DELAY) {
            beginReset()
        }
    }

    private fun tick() {
        if (state != GameState.RUNNING || ending) return
        ticks++
        val r = gameRoom as BackbreakerRoom
        when (phase) {
            Phase.SPAWN_DELAY, Phase.RESET_DELAY -> {
                if (phaseTicks <= 0) { startRound(); return }
                bossBar?.setTitle("史莱姆将在${ceil(phaseTicks / 20.0).toInt()}秒后出现")
                bossBar?.progress = (phaseTicks.toDouble() / (if (phase == Phase.SPAWN_DELAY) maxOf(1, r.spawnDelaySeconds * 20) else 100)).coerceIn(0.0, 1.0)
                if (--phaseTicks <= 0) startRound()
            }
            Phase.HUNTING -> {
                countdown()
                if (phase != Phase.HUNTING || ending) return
                val entity = slime ?: return
                val nearest = alive.mapNotNull { Bukkit.getPlayer(it) }
                    .filter { it.world == entity.world }
                    .minByOrNull { it.location.distanceSquared(entity.location) }
                if (nearest != null) {
                    val distance = nearest.location.distanceSquared(entity.location)
                    if (distance <= r.attachDistance * r.attachDistance && hasLineOfSight(entity, nearest)) {
                        phase = Phase.ATTACHING
                        phaseTicks = 0
                        holder = nearest.uniqueId
                        flightStart = entity.location.clone()
                        entity.world.playSound(entity.location, Sound.ENTITY_SLIME_SQUISH, 1f, 1.2f)
                    } else navigator?.step(entity.location, nearest.location)?.let(::moveSlime)
                }
            }
            Phase.ATTACHING -> {
                countdown()
                if (phase != Phase.ATTACHING || ending) return
                val entity = slime ?: return
                val target = holder?.let(Bukkit::getPlayer)
                if (target == null || target.uniqueId !in alive) { beginReset(); return }
                phaseTicks++
                val destination = headSlimeLocation(target)
                val start = flightStart ?: entity.location
                moveSlime(start.clone().add(destination.toVector().subtract(start.toVector()).multiply((phaseTicks / 10.0).coerceAtMost(1.0))))
                if (phaseTicks >= 10) attach(target)
            }
            Phase.ATTACHED -> {
                countdown()
                if (phase != Phase.ATTACHED || ending) return
                val target = holder?.let(Bukkit::getPlayer)
                if (target == null || target.uniqueId !in alive) { beginReset(); return }
                moveSlime(headSlimeLocation(target))
                phaseTicks++
                if (phaseTicks == r.throwDelaySeconds * 20) {
                    target.playSound(target.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f)
                    PlayerExt(target).actionBarSender().primary("现在可以左键扔出史莱姆！")
                }
            }
            Phase.THROWN -> {
                countdown()
                if (phase != Phase.THROWN || ending) return
                phaseTicks++
                val entity = slime ?: return
                if (phaseTicks >= 80 || entity.location.y < entity.world.minHeight) {
                    beginReset()
                } else if (advanceThrow(entity)) {
                    thrownVelocity = null
                    navigator?.reset()
                    phase = Phase.HUNTING
                    entity.world.playSound(entity.location, Sound.ENTITY_SLIME_SQUISH, 1f, 0.8f)
                }
            }
        }
    }

    private fun countdown() {
        val r = gameRoom as BackbreakerRoom
        if (!lethal) {
            roundTicks++
            if (roundTicks >= r.roundSeconds * 20) {
                lethal = true
                sender.warn("时间到！史莱姆下次附身将淘汰玩家！")
                players.forEach { it.player.playSound(it.player.location, Sound.BLOCK_BELL_USE, 1f, 0.7f) }
                if (phase == Phase.ATTACHED) {
                    holder?.let(Bukkit::getPlayer)?.let { eliminate(PlayerExt(it)) }
                    return
                }
            }
        }
        bossBar?.setTitle(if (lethal) "危险！下次附身立刻淘汰" else "第${round}轮 · 剩余${ceil((r.roundSeconds * 20 - roundTicks) / 20.0).toInt()}秒")
        bossBar?.progress = if (lethal) 0.0 else (1.0 - roundTicks.toDouble() / (r.roundSeconds * 20)).coerceIn(0.0, 1.0)
        bossBar?.color = if (lethal) BarColor.RED else BarColor.GREEN
    }

    private fun attach(player: Player) {
        if (lethal) { eliminate(PlayerExt(player)); return }
        phase = Phase.ATTACHED
        phaseTicks = 0
        applyEffects(player, true)
        player.world.playSound(player.location, Sound.ENTITY_SLIME_SQUISH, 1.3f, 0.6f)
        player.world.spawnParticle(Particle.ITEM_SLIME, player.eyeLocation, 18, 0.3, 0.3, 0.3, 0.1)
        sender.warn("${player.name}被史莱姆附身了！")
    }

    private fun hasLineOfSight(entity: BlockDisplay, player: Player): Boolean {
        val from = entity.location.clone().add(0.0, 0.8, 0.0)
        val to = player.eyeLocation
        val direction = to.toVector().subtract(from.toVector())
        return from.world.rayTraceBlocks(from, direction, direction.length()) == null
    }

    private fun moveSlime(location: Location) {
        // 玩家视角的位置包含 yaw/pitch；Display 只应跟随位置，不随视角旋转。
        location.yaw = 0f
        location.pitch = 0f
        slime?.teleport(location)
    }

    /** 方块底面中心是 Display 的位置，使方块中心与玩家眼睛重合。 */
    private fun headSlimeLocation(player: Player) = player.eyeLocation.clone().add(0.0, -0.5, 0.0)

    /** Display 没有生物的速度和落地状态，投掷期间自行计算弹道。 */
    private fun advanceThrow(entity: BlockDisplay): Boolean {
        val velocity = thrownVelocity ?: return false
        val current = entity.location
        val next = current.clone().add(velocity)
        val hit = current.world.rayTraceBlocks(current.clone().add(0.0, 0.05, 0.0), velocity, velocity.length())
        if (hit?.hitBlockFace == BlockFace.UP) {
            val point = hit.hitPosition
            moveSlime(Location(current.world, point.x, hit.hitBlock!!.y + 1.0, point.z))
            return true
        }
        if (hit != null) {
            velocity.x = 0.0
            velocity.z = 0.0
            if (velocity.y > 0) velocity.y = 0.0
        } else {
            moveSlime(next)
        }
        velocity.multiply(0.98)
        velocity.y -= 0.08
        return false
    }

    private fun startRound() {
        val r = gameRoom as BackbreakerRoom
        val location = r.spawnLocation?.takeIf { it.world != null } ?: run {
            logger.error("房间${r.roomName}的出生点在游戏运行期间失效，已中止本局")
            MCZJUGameCore.getGameManager().abortGame(this)
            return
        }
        slime?.remove()
        val spawn = location.clone().apply { yaw = 0f; pitch = 0f }
        slime = location.world.spawn(spawn, BlockDisplay::class.java) { entity ->
            entity.block = Material.SLIME_BLOCK.createBlockData()
            // BlockDisplay 默认从位置的 X/Z 正方向绘制一整格方块。
            entity.setTransformationMatrix(Matrix4f().translation(-0.5f, 0f, -0.5f))
            entity.teleportDuration = 2
            entity.isGlowing = true
            entity.isPersistent = false
        }
        thrownVelocity = null
        round++
        roundTicks = 0
        lethal = false
        holder = null
        phase = Phase.HUNTING
        navigator?.reset()
        sender.info("第${round}轮开始！")
        location.world.playSound(location, Sound.ENTITY_SLIME_JUMP, 1.5f, 0.7f)
    }

    private fun beginReset() {
        slime?.remove()
        slime = null
        thrownVelocity = null
        holder = null
        phase = Phase.RESET_DELAY
        phaseTicks = 100
        lethal = false
        navigator?.reset()
        sender.info("5秒后开始下一轮。")
    }

    private fun applyEffects(player: Player, attached: Boolean) {
        val r = gameRoom as BackbreakerRoom
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION,
            if (attached) r.attachedSlowAmplifier else r.slowAmplifier, true, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.SATURATION, PotionEffect.INFINITE_DURATION, 0, true, false, false))
    }

    private fun finish() {
        if (ending) return
        ending = true
        MCZJUGameCore.getGameManager().endGame(this)
    }

    private fun announceRanking() {
        alive.forEach { survivalTicks[it] = ticks }
        val winner = alive.firstOrNull()?.let { survivors[it] }
        // Elimination can happen during the same server tick as the end of the
        // game, so the winner and runner-up can have identical tick counts.
        // Keep the last survivor first when that happens.
        val winnerId = alive.firstOrNull()
        val ranking = survivors.keys.sortedWith(
            compareByDescending<UUID> { it == winnerId }
                .thenByDescending { survivalTicks[it] ?: 0L }
                .thenBy { survivors[it] }
        )
        sender.success(if (winner == null) "本局无人获胜" else "${winner}获胜！")
        val lines = ranking.mapIndexed { i, id -> "#${i + 1} ${survivors[id]}：${"%.2f".format((survivalTicks[id] ?: 0L) / 20.0)}秒" }
        sender.info("最终存活时间排名：\n${lines.joinToString("\n")}")
    }

    private fun cleanup(returnToLobby: Boolean) {
        task?.cancel(); task = null
        bossBar?.removeAll(); bossBar = null
        slime?.remove(); slime = null
        thrownVelocity = null
        val gamePlayers = players.toList()
        gamePlayers.forEach { ext ->
            if (!ext.player.isOnline) return@forEach
            ext.resetState()
            ext.player.gameMode = GameMode.ADVENTURE
        }
        if (returnToLobby) {
            Bukkit.getScheduler().runTask(McBackbreaker.instance, Runnable {
                gamePlayers.forEach { ext -> if (ext.player.isOnline) ext.player.performCommand("lobby $id") }
            })
        }
        alive.clear()
        survivors.clear()
        survivalTicks.clear()
        holder = null
        flightStart = null
        navigator = null
        active.remove(this)
    }

    companion object {
        private val active = mutableSetOf<BackbreakerGame>()
        fun isGameSlime(entity: org.bukkit.entity.Entity): Boolean = active.any { it.slime?.uniqueId == entity.uniqueId }
        fun shutdownAll() { active.toList().forEach { MCZJUGameCore.getGameManager().abortGame(it) } }
    }

    private class DeathStrategy(game: BackbreakerGame) : AbstractPlayerDeathStrategy(game) {
        private val owner = game
        override fun onPlayerDeath(player: PlayerExt, event: PlayerDeathEvent) {
            event.isCancelled = true
            event.drops.clear()
            event.droppedExp = 0
            owner.eliminate(player)
        }
    }
    private class QuitStrategy(game: BackbreakerGame) : AbstractPlayerQuitStrategy(game) {
        private val owner = game
        override fun onPlayerQuit(player: PlayerExt, reason: PlayerQuitReason) { owner.eliminate(player, true) }
    }
}
