package mc.krimson.practicebot;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public final class KrimsonPracticeBot extends JavaPlugin implements Listener {
    private static final String TITLE = ChatColor.DARK_RED + "Krimson Practice Bot";
    private final Map<UUID, BotSettings> settings = new HashMap<>();
    private final Map<UUID, UUID> ownerBots = new HashMap<>();
    private final Map<UUID, UUID> botOwners = new HashMap<>();
    private NamespacedKey botKey;
    private BukkitTask distanceTask;

    @Override public void onEnable() {
        botKey = new NamespacedKey(this, "practice_bot");
        Bukkit.getPluginManager().registerEvents(this, this);
        distanceTask = Bukkit.getScheduler().runTaskTimer(this, this::checkBots, 20L, 20L);
        getLogger().info("Enabled. No world or region-name restrictions are applied.");
    }

    @Override public void onDisable() {
        if (distanceTask != null) distanceTask.cancel();
        for (UUID id : ownerBots.values()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
        ownerBots.clear();
        botOwners.clear();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("bot")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /bot.");
            return true;
        }
        if (!player.hasPermission("krimsonpracticebot.use")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        openMenu(player);
        return true;
    }

    private BotSettings cfg(Player player) {
        return settings.computeIfAbsent(player.getUniqueId(), ignored -> new BotSettings());
    }

    private String state(boolean enabled) {
        return enabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF";
    }

    private ItemStack item(Material type, String name, String... lore) {
        ItemStack stack = new ItemStack(type);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> lines = new ArrayList<>();
        for (String line : lore) lines.add(ChatColor.translateAlternateColorCodes('&', line));
        meta.setLore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    private void openMenu(Player player) {
        BotSettings s = cfg(player);
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        inv.setItem(10, item(Material.NETHERITE_HELMET, "&cHelmet",
                "&7Protection IV: " + state(!s.blast[0]), "&7Blast Protection IV: " + state(s.blast[0])));
        inv.setItem(19, item(Material.NETHERITE_CHESTPLATE, "&cChestplate",
                "&7Protection IV: " + state(!s.blast[1]), "&7Blast Protection IV: " + state(s.blast[1])));
        inv.setItem(28, item(Material.NETHERITE_LEGGINGS, "&cLeggings",
                "&7Protection IV: " + state(!s.blast[2]), "&7Blast Protection IV: " + state(s.blast[2])));
        inv.setItem(37, item(Material.NETHERITE_BOOTS, "&cBoots",
                "&7Protection IV: " + state(!s.blast[3]), "&7Blast Protection IV: " + state(s.blast[3])));

        inv.setItem(12, item(Material.FEATHER, "&bSlow Falling",
                "&7Permanent Slow Falling: " + state(s.slow), "&eClick to toggle"));
        inv.setItem(21, item(Material.SHIELD, "&bShielding",
                "&7Offhand shield: " + state(s.shield), "&eClick to toggle"));
        inv.setItem(30, item(Material.PLAYER_HEAD, "&bLook at You",
                "&7Looks at you: " + state(s.look), "&eClick to toggle"));
        inv.setItem(39, item(Material.POTION, "&bFire Resistance",
                "&7Permanent Fire Resistance: " + state(s.fire), "&eClick to toggle"));
        inv.setItem(48, item(Material.TOTEM_OF_UNDYING, "&6God Mode",
                "&7Owner invincible while bot lives: " + state(s.god), "&eClick to toggle"));

        boolean active = ownerBots.containsKey(player.getUniqueId());
        inv.setItem(49, item(active ? Material.RED_CONCRETE : Material.LIME_CONCRETE,
                active ? "&cDESPAWN BOT" : "&aSPAWN BOT",
                "&7Name: &fkrimsonprac.mc.gg", "&7Despawns beyond 200 blocks"));
        inv.setItem(50, item(Material.BARRIER, "&cClose"));
        player.openInventory(inv);
    }

    @EventHandler public void onMenuClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) return;

        BotSettings s = cfg(player);
        switch (event.getSlot()) {
            case 10 -> s.blast[0] = !s.blast[0];
            case 19 -> s.blast[1] = !s.blast[1];
            case 28 -> s.blast[2] = !s.blast[2];
            case 37 -> s.blast[3] = !s.blast[3];
            case 12 -> s.slow = !s.slow;
            case 21 -> s.shield = !s.shield;
            case 30 -> s.look = !s.look;
            case 39 -> s.fire = !s.fire;
            case 48 -> s.god = !s.god;
            case 49 -> {
                if (ownerBots.containsKey(player.getUniqueId())) despawn(player.getUniqueId(), "You despawned your bot.");
                else spawn(player);
            }
            case 50 -> { player.closeInventory(); return; }
            default -> { return; }
        }
        openMenu(player);
    }

    private void spawn(Player owner) {
        UUID ownerId = owner.getUniqueId();
        if (ownerBots.containsKey(ownerId)) {
            owner.sendMessage(ChatColor.RED + "You already have a bot.");
            return;
        }
        try {
            // The modern MANNEQUIN entity supplies the player-shaped model.
            // Spawned directly in the owner's current world/location; no region checks.
            EntityType mannequinType = EntityType.valueOf("MANNEQUIN");
            Entity raw = owner.getWorld().spawnEntity(owner.getLocation(), mannequinType);
            if (!(raw instanceof LivingEntity bot)) {
                raw.remove();
                throw new IllegalStateException("MANNEQUIN is not a LivingEntity on this Paper build.");
            }

            bot.setCustomName(ChatColor.WHITE + "krimsonprac.mc.gg");
            bot.setCustomNameVisible(true);
            bot.setSilent(true);
            bot.setPersistent(true);
            bot.setGravity(true);
            bot.getPersistentDataContainer().set(botKey, PersistentDataType.BYTE, (byte) 1);

            AttributeInstance maxHealth = bot.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(2048.0);
                bot.setHealth(2048.0);
            }
            AttributeInstance knockback = bot.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (knockback != null) knockback.setBaseValue(0.0);

            ownerBots.put(ownerId, bot.getUniqueId());
            botOwners.put(bot.getUniqueId(), ownerId);
            applySettings(bot, cfg(owner));
            owner.sendMessage(ChatColor.GREEN + "Practice bot spawned.");
        } catch (Exception exception) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not spawn practice bot. Full exception follows:", exception);
            owner.sendMessage(ChatColor.RED + "Bot spawn failed. Check the console for the full error.");
        }
    }

    private void applySettings(LivingEntity bot, BotSettings s) {
        Material[] armorTypes = {
                Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS
        };
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            armor[i] = new ItemStack(armorTypes[i]);
            ItemMeta meta = armor[i].getItemMeta();
            meta.addEnchant(s.blast[i] ? Enchantment.BLAST_PROTECTION : Enchantment.PROTECTION, 4, true);
            armor[i].setItemMeta(meta);
        }

        EntityEquipment equipment = bot.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(armor[0]);
            equipment.setChestplate(armor[1]);
            equipment.setLeggings(armor[2]);
            equipment.setBoots(armor[3]);
            equipment.setItemInMainHand(new ItemStack(Material.TOTEM_OF_UNDYING));
            equipment.setItemInOffHand(new ItemStack(s.shield ? Material.SHIELD : Material.TOTEM_OF_UNDYING));
            equipment.setHelmetDropChance(0);
            equipment.setChestplateDropChance(0);
            equipment.setLeggingsDropChance(0);
            equipment.setBootsDropChance(0);
            equipment.setItemInMainHandDropChance(0);
            equipment.setItemInOffHandDropChance(0);
        }

        if (s.slow) bot.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,
                Integer.MAX_VALUE, 0, false, false, true));
        else bot.removePotionEffect(PotionEffectType.SLOW_FALLING);

        if (s.fire) bot.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                Integer.MAX_VALUE, 0, false, false, true));
        else bot.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
    }

    @EventHandler public void onEntityDamage(EntityDamageEvent event) {
        UUID ownerId = botOwners.get(event.getEntity().getUniqueId());
        if (ownerId != null) {
            event.setCancelled(true);
            if (event instanceof EntityDamageByEntityEvent hit) {
                Entity attacker = hit.getDamager();
                Vector push = event.getEntity().getLocation().toVector()
                        .subtract(attacker.getLocation().toVector());
                if (push.lengthSquared() > 0.001) {
                    event.getEntity().setVelocity(event.getEntity().getVelocity()
                            .add(push.normalize().multiply(0.45).setY(0.22)));
                }
            }
            return;
        }
        if (event.getEntity() instanceof Player player
                && ownerBots.containsKey(player.getUniqueId()) && cfg(player).god) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onOwnerQuit(PlayerQuitEvent event) {
        despawn(event.getPlayer().getUniqueId(), null);
        settings.remove(event.getPlayer().getUniqueId());
    }

    private void checkBots() {
        for (Map.Entry<UUID, UUID> entry : new HashMap<>(ownerBots).entrySet()) {
            Player owner = Bukkit.getPlayer(entry.getKey());
            Entity bot = Bukkit.getEntity(entry.getValue());
            if (owner == null || !owner.isOnline() || bot == null) {
                despawn(entry.getKey(), null);
                continue;
            }
            if (!owner.getWorld().equals(bot.getWorld())
                    || owner.getLocation().distanceSquared(bot.getLocation()) > 40000.0) {
                despawn(entry.getKey(), ChatColor.YELLOW
                        + "Your bot despawned because you moved more than 200 blocks away.");
            } else if (cfg(owner).look && bot instanceof LivingEntity living) {
                Location here = living.getLocation();
                Vector direction = owner.getEyeLocation().toVector().subtract(here.toVector());
                if (direction.lengthSquared() > 0.01) {
                    Location facing = here.clone();
                    facing.setDirection(direction);
                    living.setRotation(facing.getYaw(), facing.getPitch());
                }
            }
        }
    }

    private void despawn(UUID ownerId, String message) {
        UUID botId = ownerBots.remove(ownerId);
        if (botId == null) return;
        botOwners.remove(botId);
        Entity bot = Bukkit.getEntity(botId);
        if (bot != null) bot.remove();
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && message != null) owner.sendMessage(message);
    }

    private static final class BotSettings {
        final boolean[] blast = new boolean[4];
        boolean slow, shield, look, fire, god;
    }
}
