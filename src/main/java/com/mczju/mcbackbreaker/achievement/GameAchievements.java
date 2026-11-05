package com.mczju.mcbackbreaker.achievement;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.achievement.AchievementDefinition;
import com.github.mczjuops.mczjugamecore.achievement.AchievementKey;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import java.util.List;
import java.util.UUID;

/** 本游戏已确认的成就定义；仅由当前局已验证的规则触发，核心负责持久化与幂等解锁。 */
public final class GameAchievements {
    private static final String GAME_ID = "mc_backbreaker_slime_trap";
    private GameAchievements() {}

    /** 游戏注册后在主线程注册全部成就。 */
    public static void register(Plugin plugin) {
        MCZJUGameCore.getAchievementManager().register(plugin, List.of(
                AchievementDefinition.challenge(new AchievementKey(GAME_ID, "hot_potato"), "烫手山芋", "同局成功甩出至少 5 次史莱姆，最终获胜", Material.SLIME_BALL),
                AchievementDefinition.challenge(new AchievementKey(GAME_ID, "last_second"), "千钧一发", "本轮爆炸前最后 1 秒内甩出，爆炸触发后仍存活", Material.CLOCK),
                AchievementDefinition.challenge(new AchievementKey(GAME_ID, "share_the_slime"), "雨露均沾", "同局自己甩出的史莱姆先后附身至少 3 名不同对手", Material.SLIME_BLOCK),
                AchievementDefinition.challenge(new AchievementKey(GAME_ID, "untouched"), "不沾锅", "至少 4 人开局，整局未被附身并最终获胜", Material.FEATHER)
        ));
    }

    /** 主线程解锁已注册成就；调用方须验证所属局、有效动作或正常结算。 */
    public static void unlock(UUID playerId, String achievementId) {
        MCZJUGameCore.getAchievementManager().unlock(playerId, new AchievementKey(GAME_ID, achievementId));
    }
}
