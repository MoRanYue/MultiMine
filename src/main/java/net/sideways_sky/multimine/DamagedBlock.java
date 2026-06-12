package net.sideways_sky.multimine;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.SoundGroup;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class DamagedBlock {
    public static int FadeStartDelay = 40;
    public static int FadeIntervalDelay = 20;
    public static float FadeDamageReduction = 0.1F;

    public DamagedBlock(Block block){
        this.block = block;
        entityId = block.hashCode();
        debugMessage("Created");
    }
    public void debugMessage(String message){
        MultiMine.debugMessage("Block[ " + entityId + " ]: " + message);
    }

    private final int entityId;
    public Map<Entity, Float> entityDamageMap = new HashMap<>();

    private final Block block;
    public void brake(Entity entity){
        MultiMine.debugMessage("Brake - " + (entity instanceof Player ? "player" : "non player"));
        if(entity instanceof Player player){
            // Capture block properties BEFORE breaking (block becomes air after breakBlock)
            SoundGroup soundGroup = block.getBlockSoundGroup();
            BlockData blockData = block.getBlockData();
            Location loc = block.getLocation().add(0.5, 0.5, 0.5);
            if(player.breakBlock(block)) {
                loc.getWorld().playSound(
                        loc,
                        soundGroup.getBreakSound(),
                        SoundCategory.BLOCKS,
                        1.0f, 1.0f
                );
                loc.getWorld().spawnParticle(
                        Particle.BLOCK,
                        loc,
                        20, 0.3, 0.3, 0.3,
                        blockData);
            }
        } else {
            block.breakNaturally(true);
        }
    }

    public float damage = 0;
    private @Nullable ScheduledTask fadeTask = null;

    public void startFade(){
        debugMessage("Fade Start");
        fadeTask = MultiMine.instance.getServer().getRegionScheduler().runAtFixedRate(MultiMine.instance, block.getLocation(), (e) -> fade(), FadeStartDelay, FadeIntervalDelay);
    }
    public void stopFade(){
        if(fadeTask != null){
            debugMessage("Fade Stop");
            fadeTask.cancel();
            fadeTask = null;
        }
    }

    public void delete(){
        stopFade();
        sendPacket(-1);
        Events.damagedBlockMap.remove(block);
        debugMessage("Deleted");
    }

    public void fade(){
        float preFadeDamageReduction = damage;
        damage -= FadeDamageReduction;
        Consumer<String> message = (messageSuffix) -> debugMessage("Fade: " + damage + " -= " + FadeDamageReduction + messageSuffix);
        if(damage < 0){
            message.accept(" -deleting");
            delete();
        } else if (Math.round(damage * 10F) != Math.round(preFadeDamageReduction * 10F)) {
            message.accept(" -updating");
            sendPacket();
        } else {
            message.accept("");
        }
    }

    public void sendPacket(){
        sendPacket(Math.round(damage * 10F));
    }
    private void sendPacket(int progress){
        // progress: -1 = clear (no damage), 0-10 = destruction stage
        // API requires progress between 0.0 and 1.0 (inclusive), where 0 = no damage
        // Use entityId as sourceId so multiple blocks can show damage simultaneously
        float damage = Math.max(0.0F, progress / 10.0F);
        for (Player player : block.getWorld().getPlayers()) {
            player.sendBlockDamage(block.getLocation(), damage, entityId);
        }
    }

}
