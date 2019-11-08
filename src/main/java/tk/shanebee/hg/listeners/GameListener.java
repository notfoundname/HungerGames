package tk.shanebee.hg.listeners;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import tk.shanebee.hg.HG;
import tk.shanebee.hg.Status;
import tk.shanebee.hg.data.Config;
import tk.shanebee.hg.data.Language;
import tk.shanebee.hg.data.Leaderboard;
import tk.shanebee.hg.data.PlayerData;
import tk.shanebee.hg.events.ChestOpenEvent;
import tk.shanebee.hg.game.Game;
import tk.shanebee.hg.managers.KillManager;
import tk.shanebee.hg.util.Util;

import java.util.UUID;

/**
 * Internal event listener
 */
public class GameListener implements Listener {

	private HG plugin;
	private Language lang;
	private String tsn = ChatColor.GOLD + "TrackingStick " + ChatColor.GREEN + "Uses: ";
	private ItemStack trackingStick;
	//private HashMap<Player, Entity> killerMap = new HashMap<>(); ON HOLD for now
    private KillManager killManager;

	public GameListener(HG plugin) {
		this.plugin = plugin;
		this.lang = plugin.getLang();
		ItemStack it = new ItemStack(Material.STICK, 1);
		ItemMeta im = it.getItemMeta();
		assert im != null;
		im.setDisplayName(tsn + Config.trackingstickuses);
		it.setItemMeta(im);
		trackingStick = it;
        killManager = plugin.getKillManager();
	}

	private void dropInv(Player p) {
		PlayerInventory inv = p.getInventory();
		Location l = p.getLocation();
		for (ItemStack i : inv.getContents()) {
			if (i != null && i.getType() != Material.AIR) {
				assert l.getWorld() != null;
				l.getWorld().dropItemNaturally(l, i);
			}
		}
		for (ItemStack i : inv.getArmorContents()) {
			if (i != null && i.getType() != Material.AIR) {
				assert l.getWorld() != null;
				l.getWorld().dropItemNaturally(l, i);
			}
		}
	}

	private void checkStick(Game g) {
		if (Config.playersfortrackingstick == g.getPlayers().size()) {
			for (UUID u : g.getPlayers()) {
				Player p = Bukkit.getPlayer(u);
				if (p != null) {
					Util.scm(p, HG.getPlugin().getLang().track_bar);
					Util.scm(p, HG.getPlugin().getLang().track_new1);
					Util.scm(p, HG.getPlugin().getLang().track_new2);
					Util.scm(p, HG.getPlugin().getLang().track_bar);
					p.getInventory().addItem(trackingStick);
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	private void onAttack(EntityDamageByEntityEvent event) {
		Entity defender = event.getEntity();
		Entity damager = event.getDamager();

		if (damager instanceof Player) {
			if (plugin.getSpectators().containsKey(damager.getUniqueId())) {
				event.setCancelled(true);
				return;
			}
		}
		if (defender instanceof Player) {
			Player player = (Player) defender;
			PlayerData pd = plugin.getPlayers().get(player.getUniqueId());

			if (pd != null) {
				Game game = pd.getGame();

				if (game.getStatus() != Status.RUNNING) {
					event.setCancelled(true);
				} else if (pd.isOnTeam(player.getUniqueId()) && damager instanceof Player && pd.getTeam().isOnTeam(damager.getUniqueId())) {
					Util.scm(damager, "&c" + player.getName() + " is on your team!");
					event.setCancelled(true);
				} else if (event.getFinalDamage() >= player.getHealth()) {
					event.setCancelled(true);
					processDeath(player, game, damager, event.getCause());
				}
			}
		}
	}

	@EventHandler(priority =  EventPriority.HIGHEST)
	private void onDeathByOther(EntityDamageEvent event) {
		if (event.getEntity() instanceof Player) {
			final Player player = ((Player) event.getEntity());
			if (plugin.getSpectators().containsKey(player.getUniqueId())) {
				event.setCancelled(true);
				player.setFireTicks(0);
				return;
			}
			if (event instanceof EntityDamageByEntityEvent) return;
			PlayerData pd = plugin.getPlayers().get(player.getUniqueId());
			if (pd != null) {
				if (event.getFinalDamage() >= player.getHealth()) {
					event.setCancelled(true);
					processDeath(player, pd.getGame(), null, event.getCause());
				}
			}
		}
	}

	private void processDeath(Player player, Game game, Entity damager, EntityDamageEvent.DamageCause cause) {
		dropInv(player);
		player.setHealth(20);
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (damager instanceof Player) {
				game.addKill(((Player) damager));
				plugin.getLeaderboard().addStat(((Player) damager), Leaderboard.Stats.KILLS);
				game.msgAll(HG.getPlugin().getLang().death_fallen + " &d" + killManager.getKillString(player.getName(), damager));
			} else if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
				game.msgAll(HG.getPlugin().getLang().death_fallen + " &d" + killManager.getKillString(player.getName(), damager));
			} else if (cause == EntityDamageEvent.DamageCause.PROJECTILE) {
				game.msgAll(HG.getPlugin().getLang().death_fallen + " &d" + killManager.getKillString(player.getName(), damager));
				if (killManager.isShotByPlayer(damager) && killManager.getShooter(damager) != player) {
				    game.addKill(killManager.getShooter(damager));
                    plugin.getLeaderboard().addStat(killManager.getShooter(damager), Leaderboard.Stats.KILLS);
                }
			} else {
				game.msgAll(HG.getPlugin().getLang().death_fallen + " &d" + killManager.getDeathString(cause, player.getName()));
			}
			plugin.getLeaderboard().addStat(player, Leaderboard.Stats.DEATHS);
			plugin.getLeaderboard().addStat(player, Leaderboard.Stats.GAMES);

			for (UUID uuid : game.getPlayers()) {
				Player alive = Bukkit.getPlayer(uuid);
				if (alive != null && player != alive) {
					alive.playSound(alive.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 5, 1);
				}
			}

			game.leave(player, true);
			game.runCommands(Game.CommandType.DEATH, player);

			Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> checkStick(game), 40L);
		}, 1);

	}

	@EventHandler
	private void onSprint(FoodLevelChangeEvent event) {
		Player p = (Player) event.getEntity();
		if (plugin.getPlayers().containsKey(p.getUniqueId())) {
			Status st = plugin.getPlayers().get(p.getUniqueId()).getGame().getStatus();
			if (st == Status.WAITING || st == Status.COUNTDOWN) {
				event.setFoodLevel(1);
				event.setCancelled(true);
			}
		}
	}

	private void useTrackStick(Player p) {
		ItemStack i = p.getInventory().getItemInMainHand();
		ItemMeta im = i.getItemMeta();
		assert im != null;
		im.getDisplayName();
		if (im.getDisplayName().startsWith(tsn)) {
			int uses = Integer.parseInt(im.getDisplayName().replace(tsn, ""));
			if (uses == 0) {
				Util.scm(p, HG.getPlugin().getLang().track_empty);
			} else {
				PlayerData pd = plugin.getPlayers().get(p.getUniqueId());
				final Game g = pd.getGame();
				for (Entity e : p.getNearbyEntities(120, 50, 120)) {
					if (e instanceof Player) {
						if (!g.getPlayers().contains(e.getUniqueId())) continue;
						im.setDisplayName(tsn + (uses - 1));
						Location l = e.getLocation();
						int range = (int) p.getLocation().distance(l);
						Util.scm(p, HG.getPlugin().getLang().track_nearest
								.replace("<player>", e.getName())
								.replace("<range>", String.valueOf(range))
								.replace("<location>", getDirection(p.getLocation().getBlock(), l.getBlock())));
						i.setItemMeta(im);
						p.updateInventory();
						return;
					}
				}
				Util.scm(p, HG.getPlugin().getLang().track_no_near);
			}
		}
	}

	private String getDirection(Block block, Block block1) {
		Vector bv = block.getLocation().toVector();
		Vector bv2 = block1.getLocation().toVector();
		float y = (float) angle(bv.getX(), bv.getZ(), bv2.getX(), bv2.getZ());
		float cal = (y * 10);
		int c = (int) cal;
		if (c <= 1 && c >= -1) {
			return "South";
		} else if (c > -14 && c < -1) {
			return "SouthWest";
		} else if (c >= -17 && c <= -14) {
			return "West";
		} else if (c > -29 && c < -17) {
			return "NorthWest";
		} else if (c > 17 && c < 29) {
			return "NorthEast";
		} else if (c <= 17 && c >= 14) {
			return "East";
		} else if (c > 1 && c < 14) {
			return "SouthEast";
		} else if (c <= 29 && c >= -29) {
			return "North";
		} else {
			return "UnKnown";
		}
	}

	private double angle(double d, double e, double f, double g) {
		//Vector differences
		int x = (int) (f - d);
		int z = (int) (g - e);

		return Math.atan2(x, z);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	private void onTarget(EntityTargetEvent event) {
		Entity target = event.getTarget();
		if (target instanceof Player) {
			if (plugin.getSpectators().containsKey(target.getUniqueId())) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler
	private void onChestOpen(ChestOpenEvent event) {
		Block b = event.getChest();
		Game g = event.getGame();
		if (!g.isLoggedChest(b.getLocation())) {
			HG.getPlugin().getManager().fillChests(b, g, event.isBonus());
			g.addGameChest(b.getLocation());
		}
	}

	@EventHandler
	private void onChestUse(PlayerInteractEvent event) {
		Player p = event.getPlayer();
		if (event.getAction() == Action.RIGHT_CLICK_BLOCK && plugin.getPlayers().containsKey(p.getUniqueId())) {
			Block block = event.getClickedBlock();
			assert block != null;
			PlayerData pd = plugin.getPlayers().get(p.getUniqueId());
			if (block.getType() == Material.CHEST) {
				Bukkit.getServer().getPluginManager().callEvent(new ChestOpenEvent(pd.getGame(), block, false));
			}
			if (block.getType() == Material.TRAPPED_CHEST || block.getState() instanceof ShulkerBox) {
				Bukkit.getServer().getPluginManager().callEvent(new ChestOpenEvent(pd.getGame(), block, true));
			}
			if (Util.isRunningMinecraft(1, 14) && block.getType() == Material.BARREL) {
					Bukkit.getServer().getPluginManager().callEvent(new ChestOpenEvent(pd.getGame(), block, true));
			}
		}
	}

	@EventHandler
	private void onItemUseAttempt(PlayerInteractEvent event) {
		Player p = event.getPlayer();
		if (plugin.getSpectators().containsKey(p.getUniqueId())) {
			event.setCancelled(true);
            if (isSpectatorCompass(event)) {
                handleSpectatorCompass(p);
                return;
            }
		}
		if (event.getAction() != Action.PHYSICAL && plugin.getPlayers().containsKey(p.getUniqueId())) {
			Status st = plugin.getPlayers().get(p.getUniqueId()).getGame().getStatus();
			if (st == Status.WAITING || st == Status.COUNTDOWN) {
				event.setCancelled(true);
				Util.scm(p, HG.getPlugin().getLang().listener_no_interact);
			}
		}
	}

    private boolean isSpectatorCompass(PlayerInteractEvent event) {
        Action action = event.getAction();
        Player player = event.getPlayer();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return false;
        if (!plugin.getSpectators().containsKey(player.getUniqueId())) return false;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.COMPASS) return false;
        return item.getItemMeta() != null && item.getItemMeta().getDisplayName().equalsIgnoreCase(Util.getColString(lang.spectator_compass));

    }

    private void handleSpectatorCompass(Player player) {
        Game game = plugin.getSpectators().get(player.getUniqueId()).getGame();
        game.getSpectatorGUI().openInventory(player);
    }

	@EventHandler
	private void onPlayerClickLobby(PlayerInteractEvent event) {
		Player p = event.getPlayer();
		if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
			Block b = event.getClickedBlock();
			assert b != null;
			if (Util.isWallSign(b.getType())) {
				Sign sign = (Sign) b.getState();
				if (sign.getLine(0).equals(Util.getColString(lang.lobby_sign_1_1))) {
					Game game = HG.getPlugin().getManager().getGame(sign.getLine(1).substring(2));
					if (game == null) {
						Util.scm(p, HG.getPlugin().getLang().cmd_delete_noexist);
					} else {
						if (p.getInventory().getItemInMainHand().getType() == Material.AIR) {
							game.join(p);
						} else {
							Util.scm(p, HG.getPlugin().getLang().listener_sign_click_hand);
						}
					}
				}
			}
		} else if (event.getAction().equals(Action.LEFT_CLICK_AIR)) {
			if (p.getInventory().getItemInMainHand().getType().equals(Material.STICK) && plugin.getPlayers().containsKey(p.getUniqueId())) {
				useTrackStick(p);
			}
		}
	}

	@EventHandler
	private void blockPlace(BlockPlaceEvent event) {
		Player p = event.getPlayer();
		Block b = event.getBlock();

		if (plugin.getSpectators().containsKey(p.getUniqueId())) {
			event.setCancelled(true);
		}
		if (HG.getPlugin().getManager().isInRegion(b.getLocation())) {

			if (Config.breakblocks && plugin.getPlayers().containsKey(p.getUniqueId())) {
                Game g = plugin.getPlayers().get(p.getUniqueId()).getGame();
				if (g.getStatus() == Status.RUNNING || g.getStatus() == Status.BEGINNING) {
					if (!Config.blocks.contains(b.getType().toString()) && !Config.blocks.contains("ALL")) {
						Util.scm(p, HG.getPlugin().getLang().listener_no_edit_block);
						event.setCancelled(true);
					} else {
						g.recordBlockPlace(event.getBlockReplacedState());
						if (isChest(b)) {
                            g.addPlayerChest(b.getLocation());
                        }
					}
				} else {
					Util.scm(p, HG.getPlugin().getLang().listener_not_running);
					event.setCancelled(true);
				}
			} else {
				if (p.hasPermission("hg.create")) {
				    Game g = plugin.getManager().getGame(b.getLocation());
				    Status status = g.getStatus();
				    switch (status) {
                        case BEGINNING:
                        case RUNNING:
                            g.recordBlockPlace(event.getBlockReplacedState());
                        default:
                            return;
                    }
                }
				event.setCancelled(true);
			}
		}
	}

	@EventHandler
	private void blockBreak(BlockBreakEvent event) {
		Player p = event.getPlayer();
		Block b = event.getBlock();

		if (plugin.getSpectators().containsKey(p.getUniqueId())) {
			event.setCancelled(true);
		}
		if (HG.getPlugin().getManager().isInRegion(b.getLocation())) {

			if (Config.breakblocks && plugin.getPlayers().containsKey(p.getUniqueId())) {
                Game g = plugin.getPlayers().get(p.getUniqueId()).getGame();
				if (g.getStatus() == Status.RUNNING || !Config.protectCooldown) {
					if (!Config.blocks.contains(b.getType().toString()) && !Config.blocks.contains("ALL")) {
						Util.scm(p, HG.getPlugin().getLang().listener_no_edit_block);
						event.setCancelled(true);
					} else {
						g.recordBlockBreak(b);
						if (isChest(b)) {
                            g.removeGameChest(b.getLocation());
                            g.removePlayerChest(b.getLocation());
                        }
					}
				} else {
					Util.scm(p, HG.getPlugin().getLang().listener_not_running);
					event.setCancelled(true);
				}
			} else {
                if (!plugin.getPlayers().containsKey(p.getUniqueId()) && p.hasPermission("hg.create")) {
                    Game g = HG.getPlugin().getManager().getGame(b.getLocation());
                    Status status = g.getStatus();
                    switch (status) {
                        case BEGINNING:
                        case RUNNING:
                            g.removeGameChest(b.getLocation());
                        default:
                            return;
                    }
                }
				event.setCancelled(true);
			}
		}
	}

	@EventHandler
    private void onBucketEmpty(PlayerBucketEmptyEvent event) {
	    handleBucketEvent(event, false);
    }

    @EventHandler
    private void onBucketDrain(PlayerBucketFillEvent event) {
	    handleBucketEvent(event, true);
    }

    private void handleBucketEvent(PlayerBucketEvent event, boolean fill) {
	    Block block;
	    if (Util.methodExists(PlayerBucketEvent.class, "getBlock")) {
	        block = event.getBlock();
        } else {
	        block = event.getBlockClicked().getRelative(event.getBlockFace());
        }
	    Player player = event.getPlayer();
	    final boolean WATER = event.getBucket() == Material.WATER_BUCKET && Config.blocks.contains("WATER");
	    final boolean LAVA = event.getBucket() == Material.LAVA_BUCKET && Config.blocks.contains("LAVA");

        if (plugin.getManager().isInRegion(block.getLocation())) {
            if (Config.breakblocks && plugin.getPlayers().containsKey(player.getUniqueId())) {
                Game game = plugin.getPlayers().get(player.getUniqueId()).getGame();
                if (game.getStatus() == Status.RUNNING || !Config.protectCooldown) {
                    if (fill && (Config.blocks.contains(block.getType().toString()) || Config.blocks.contains("ALL"))) {
                        game.recordBlockBreak(block);
                    } else if (!fill && (WATER || LAVA || Config.blocks.contains("ALL"))) {
                        game.recordBlockPlace(block.getState());
                    } else {
                        Util.scm(player, plugin.getLang().listener_no_edit_block);
                        event.setCancelled(true);
                    }
                } else {
                    Util.scm(player, plugin.getLang().listener_not_running);
                    event.setCancelled(true);
                }
            } else {
                if (plugin.getPlayers().containsKey(player.getUniqueId()) || !player.hasPermission("hg.create")) {
                    event.setCancelled(true);
                }
            }
        }
    }

    private boolean isChest(Block block) {
        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST || block.getState() instanceof Shulker) {
            return true;
        }
        return Util.isRunningMinecraft(1, 14) && block.getType() == Material.BARREL;
    }

	@EventHandler
	private void onBlockFall(BlockPhysicsEvent event) {
		Block block = event.getBlock();
		if (Config.breakblocks && HG.getPlugin().getManager().isInRegion(block.getLocation())) {
			Game game = HG.getPlugin().getManager().getGame(block.getLocation());
			if (game.getStatus() == Status.RUNNING || game.getStatus() == Status.BEGINNING) {
				game.recordBlockBreak(block);
			}
		}
	}

	@EventHandler
	private void onFallingBlockLand(EntityChangeBlockEvent event) {
		Block block = event.getBlock();
		if (block.getType() == Material.AIR || block.getType() == Material.WATER || block.getType() == Material.LAVA) {
			if (Config.breakblocks && HG.getPlugin().getManager().isInRegion(event.getEntity().getLocation())) {
				Game game = HG.getPlugin().getManager().getGame(event.getEntity().getLocation());
				if (game.getStatus() == Status.RUNNING || game.getStatus() == Status.BEGINNING) {
					game.recordBlockPlace(block.getState());
				}
			}
		}
	}

	@EventHandler
	private void onEntityExplode(EntityExplodeEvent event) {
		if (HG.getPlugin().getManager().isInRegion(event.getLocation())) {
			Game g = HG.getPlugin().getManager().getGame(event.getLocation());
			for (Block block : event.blockList()) {
				g.recordBlockBreak(block);
			}
			event.setYield(0);
		}
	}

	@EventHandler
	private void onBlockExplode(BlockExplodeEvent event) {
		if (HG.getPlugin().getManager().isInRegion(event.getBlock().getLocation())) {
			Game g = HG.getPlugin().getManager().getGame(event.getBlock().getLocation());
			for (Block block : event.blockList()) {
				g.recordBlockBreak(block);
			}
			event.setYield(0);
		}
	}

	@EventHandler
	private void onLeafDecay(LeavesDecayEvent event) {
		if (!Config.fixleaves) return;
		Block b = event.getBlock();
		if (HG.getPlugin().getManager().isInRegion(b.getLocation())) {
			if (Config.breakblocks) {
				Game g = HG.getPlugin().getManager().getGame(b.getLocation());
				if (g.getStatus() == Status.RUNNING) {
					g.recordBlockBreak(b);
				}
			}
		}
	}

	@EventHandler
	private void onTrample(PlayerInteractEvent event) {
		if (!Config.preventtrample) return;
		Player p = event.getPlayer();
		if (plugin.getSpectators().containsKey(p.getUniqueId())) {
			event.setCancelled(true);
		}
		if (HG.getPlugin().getManager().isInRegion(p.getLocation())) {
			if (event.getAction() == Action.PHYSICAL) {
				assert event.getClickedBlock() != null;
				Material block = event.getClickedBlock().getType();
				if (block == Material.FARMLAND) {
					event.setCancelled(true);
				}
			}
		}
	}

	@EventHandler
	private void onDrop(PlayerDropItemEvent event) {
		Player p = event.getPlayer();
		if (plugin.getPlayers().containsKey(p.getUniqueId()) && plugin.getPlayers().get(p.getUniqueId()).getGame().getStatus() == Status.WAITING) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	private void onSpawn(EntitySpawnEvent event) {
		Entity entity = event.getEntity();
		if (!(entity instanceof Player) && entity instanceof LivingEntity) {
			if (HG.getPlugin().getManager().isInRegion(event.getLocation())) {
				Game g = HG.getPlugin().getManager().getGame(event.getLocation());
				if (g.getStatus() != Status.RUNNING) {
					event.setCancelled(true);
				}
				if (event instanceof CreatureSpawnEvent) {
					if (((CreatureSpawnEvent) event).getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
						event.setCancelled(true);
					}
				}
			}
		}
		if (!(entity instanceof Player)) {
			if (HG.getPlugin().getManager().isInRegion(event.getLocation())) {
				Game g = HG.getPlugin().getManager().getGame(event.getLocation());
				g.getBound().addEntity(entity);
			}
		}
	}

	@EventHandler
	private void onPickup(EntityPickupItemEvent event) {
		if (event.getEntity() instanceof Player) {
			if (plugin.getSpectators().containsKey(event.getEntity().getUniqueId())) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler
	private void onLogout(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		if (plugin.getPlayers().containsKey(player.getUniqueId())) {
			plugin.getPlayers().get(player.getUniqueId()).getGame().leave(player, false);
		}
		if (plugin.getSpectators().containsKey(player.getUniqueId())) {
			plugin.getSpectators().get(player.getUniqueId()).getGame().leaveSpectate(player);
		}
	}

	@EventHandler
	private void onEntityShoot(EntityShootBowEvent event) {
		if (event.getEntity().hasMetadata("death-message")) {
			event.getProjectile().setMetadata("death-message",
					new FixedMetadataValue(plugin, event.getEntity().getMetadata("death-message").get(0).asString()));
		}
		if (event.getEntity() instanceof Player && plugin.getPlayers().containsKey(event.getEntity().getUniqueId())) {
		    event.getProjectile().setMetadata("shooter", new FixedMetadataValue(plugin, event.getEntity().getName()));
        }
	}

	@EventHandler
	private void onChat(AsyncPlayerChatEvent event) {
		if (!Config.spectateChat) {
			Player spectator = event.getPlayer();
			if (HG.getPlugin().getSpectators().containsKey(spectator.getUniqueId())) {
				PlayerData data = HG.getPlugin().getSpectators().get(spectator.getUniqueId());
				Game game = data.getGame();
				for (UUID uuid : game.getPlayers()) {
					Player player = Bukkit.getPlayer(uuid);
					event.getRecipients().remove(player);
				}
			}
		}
	}

	@EventHandler
	private void onTeleportIntoArena(PlayerTeleportEvent event) {
		Player player = event.getPlayer();
		Location location = event.getTo();
		for (Game game : plugin.getGames()) {
			if (game.isInRegion(location) && game.getStatus() == Status.RUNNING) {
				if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && !game.getPlayers().contains(player.getUniqueId()) && !game.getSpectators().contains(player.getUniqueId())) {
					event.setCancelled(true);
				}
			}
		}
	}

}