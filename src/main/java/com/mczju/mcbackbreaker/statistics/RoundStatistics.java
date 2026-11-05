package com.mczju.mcbackbreaker.statistics;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.event.GameResultEvent;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.data.JsonPlayerData;
import com.github.mczjuops.mczjugamecore.statistics.StatisticDisplay;
import com.github.mczjuops.mczjugamecore.statistics.StatisticKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import java.util.*;

/** 本局结果去重与子插件指标；仅在服务器主线程使用，不重复采集核心的参赛/时间/原版死亡。 */
public final class RoundStatistics {
    private static final String GAME_ID = "mc_backbreaker_slime_trap";
    private final AbstractGame game;
    private final Set<UUID> settled = new HashSet<>();

    /** 由每个游戏实例独立持有，禁止跨局复用。 */
    public RoundStatistics(AbstractGame game) { this.game = game; }

    /** 游戏注册后注册数据及菜单展示；不注册任何成就。 */
    public static void register(Plugin owner) {
        MCZJUGameCore.getPlayerDataManager().registerPlayerData(GAME_ID + "_statistics", Data.class);
        MCZJUGameCore.getGameStatisticsManager().register(owner, List.of(
                display("throws", "成功甩出史莱姆次数", Material.SLIME_BALL),
                display("best_survival_millis", "最长存活时间", Material.CLOCK)
        ));
    }

    private static StatisticDisplay display(String key, String name, Material icon) {
        return StatisticDisplay.of(new StatisticKey(GAME_ID, key), name, icon, id -> {
            long value = data(id).values.getOrDefault(key, 0L);
            return key.endsWith("_millis")
                    ? (value == 0 ? null : String.format(Locale.ROOT, "%.2f 秒", value / 1000.0))
                    : Long.toString(value);
        });
    }

    /** 每名玩家每局只结算一次；null 表示仅保存指标、没有胜负规则。取消/中断不调用。 */
    public boolean complete(UUID id, GameResultEvent.Outcome outcome) {
        if (!settled.add(id)) return false;
        if (outcome != null) Bukkit.getPluginManager().callEvent(new GameResultEvent(game, id, outcome));
        return true;
    }

    /** 增加非负的玩法计数，调用方负责验证当前游戏及动作有效性。 */
    public static void add(UUID id, String key, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative metric increment");
        Data data = data(id);
        data.values.put(key, Math.addExact(data.values.getOrDefault(key, 0L), amount));
        data.setModified(true);
    }

    /** 保存历史最大值；零保留为暂无纪录。 */
    public static void best(UUID id, String key, long value) {
        if (value <= data(id).values.getOrDefault(key, 0L)) return;
        data(id).values.put(key, value);
        data(id).setModified(true);
    }

    /** 保存最短正用时，旧纪录为零时视为尚未完赛。 */
    public static void fastest(UUID id, String key, long millis) {
        long old = data(id).values.getOrDefault(key, 0L);
        if (millis <= 0 || (old > 0 && old <= millis)) return;
        data(id).values.put(key, millis);
        data(id).setModified(true);
    }

    private static Data data(UUID id) {
        return MCZJUGameCore.getPlayerDataManager().getPlayerData(id.toString(), Data.class);
    }

    /** 独立持久化命名空间，保留已有排行榜数据结构；字段由 MGC JSON 序列化。 */
    public static final class Data extends JsonPlayerData {
        public Map<String, Long> values = new LinkedHashMap<>();
        public Data() {}
    }
}
