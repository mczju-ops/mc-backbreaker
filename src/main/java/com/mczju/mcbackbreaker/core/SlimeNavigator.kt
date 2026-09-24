package com.mczju.mcbackbreaker.core

import org.bukkit.Location
import org.bukkit.World
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.floor

/** 在地面方格上寻路；每个候选点先检查支撑方块和史莱姆占用空间。 */
internal class SlimeNavigator(private val room: BackbreakerRoom) {
    private data class Cell(val x: Int, val y: Int, val z: Int)
    private var path = ArrayDeque<Location>()
    private var lastTarget: Cell? = null
    private var pathAge = 0

    fun reset() { path.clear(); lastTarget = null; pathAge = 0 }

    fun step(current: Location, target: Location): Location? {
        if (current.world != target.world) return null
        val targetCell = cell(target)
        if (path.isEmpty() || lastTarget != targetCell || ++pathAge >= 10) {
            path = findPath(current, target)
            lastTarget = targetCell
            pathAge = 0
        }
        while (path.isNotEmpty() && path.first.distanceSquared(current) < 0.06) path.removeFirst()
        val next = path.firstOrNull() ?: return null
        val delta = next.toVector().subtract(current.toVector())
        if (delta.lengthSquared() < 0.0001) return null
        val distance = minOf(room.slimeSpeed, delta.length())
        val destination = current.clone().add(delta.normalize().multiply(distance))
        // 使用碰撞检测限制插值移动，避免在拐角穿过方块。
        if (clearAt(current.world, destination.blockX, destination.blockY, destination.blockZ)) {
            return destination
        }
        reset()
        return null
    }

    private fun findPath(start: Location, goal: Location): ArrayDeque<Location> {
        val world = start.world
        val origin = cell(start)
        val destination = cell(goal)
        val queue = ArrayDeque<Cell>()
        val parent = mutableMapOf<Cell, Cell?>()
        queue.add(origin)
        parent[origin] = null
        var best = origin
        var bestScore = score(origin, destination)
        while (queue.isNotEmpty() && parent.size < 900) {
            val here = queue.removeFirst()
            if (score(here, destination) <= 1) { best = here; break }
            for ((dx, dz) in DIRECTIONS) {
                val x = here.x + dx
                val z = here.z + dz
                if (abs(x - origin.x) > 20 || abs(z - origin.z) > 20) continue
                val diagonal = dx != 0 && dz != 0
                // 斜向移动时两侧都要有可站立空间，不能从障碍物的角上穿过去。
                if (diagonal && (!canStand(world, Cell(x, here.y, here.z)) ||
                            !canStand(world, Cell(here.x, here.y, z)))) continue
                for (dy in intArrayOf(0, 1, -1)) {
                    // 对角线只用于同一高度；上下台阶仍按直线方向处理。
                    if (diagonal && dy != 0) continue
                    val next = Cell(x, here.y + dy, z)
                    if (next in parent || !canStand(world, next)) continue
                    if (dy > 0 && !clearAt(world, here.x, here.y + 1, here.z)) continue
                    parent[next] = here
                    queue.add(next)
                    val candidate = score(next, destination)
                    if (candidate < bestScore) { bestScore = candidate; best = next }
                    break
                }
            }
        }
        val result = ArrayDeque<Location>()
        var cursor: Cell? = best
        while (cursor != null && cursor != origin) {
            result.addFirst(Location(world, cursor.x + 0.5, cursor.y.toDouble(), cursor.z + 0.5))
            cursor = parent[cursor]
        }
        return result
    }

    private fun score(a: Cell, b: Cell) = abs(a.x - b.x) + abs(a.z - b.z) + abs(a.y - b.y) * 2
    private fun cell(location: Location) = Cell(location.blockX, floor(location.y).toInt(), location.blockZ)
    private fun canStand(world: World, cell: Cell): Boolean =
        world.getBlockAt(cell.x, cell.y - 1, cell.z).type.isSolid && clearAt(world, cell.x, cell.y, cell.z)

    private fun clearAt(world: World, x: Int, y: Int, z: Int): Boolean =
        world.getBlockAt(x, y, z).isPassable && world.getBlockAt(x, y + 1, z).isPassable

    companion object { private val DIRECTIONS = listOf(
        1 to 1, 1 to -1, -1 to 1, -1 to -1,
        1 to 0, -1 to 0, 0 to 1, 0 to -1
    ) }
}
