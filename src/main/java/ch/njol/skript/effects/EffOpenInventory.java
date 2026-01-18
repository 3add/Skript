package ch.njol.skript.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.util.Version;
import ch.njol.util.Kleenean;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

@Name("Open/Close Inventory")
@Description({"Opens an inventory to a player.  The player can then access and modify the inventory as if it was a chest that they just opened.",
	"Please note that currently 'show' and 'open' have the same effect, but 'show' will eventually show an unmodifiable view of the inventory in the future."})
@Examples({"show the victim's inventory to the player",
	"open the player's inventory for the player"})
@Since("2.0, 2.1.1 (closing), 2.2-Fixes-V10 (anvil), 2.4 (hopper, dropper, dispenser), 2.14.0 (using more stable api)")
public class EffOpenInventory extends Effect {

	static {
		Skript.registerEffect(EffOpenInventory.class,
			"(open|show) ((0¦(crafting [table]|workbench)|1¦chest|2¦anvil|3¦hopper|4¦dropper|5¦dispenser) (view|window|inventory|)|%-inventory/inventorytype%) (to|for) %players%",
			"close [the] inventory [view] (to|of|for) %players%", "close %players%'[s] inventory [view]");
	}

	// Even though the actual api was added in 1.21.1, back then there wasn't support for creating inventory views with a null title.
	// Fallback to the older Bukkit api is thus done to avoid errors.
	// See bugged (1.21.1 - 1.21.3): https://github.com/PaperMC/Paper/blob/fbea3cdc0caca69814e5ab68b981fa0bdbe5331d/paper-server/src/main/java/org/bukkit/craftbukkit/inventory/CraftMenuType.java
	// See Fixed (1.21.4+): https://github.com/PaperMC/Paper/blob/8eb8e44ac32a99f53da7af50e800ac8831030580/paper-server/src/main/java/org/bukkit/craftbukkit/inventory/CraftMenuType.java
	private static final boolean SUPPORT_MENU_TYPE = Skript.classExists("org.bukkit.inventory.MenuType")
		&& Skript.getMinecraftVersion().isLargerThan(new Version(1, 21, 3));

	private boolean open;
	private InventoryType inventoryType;
	private @Nullable Expression<?> inventoryExpr;

	private Expression<Player> players;

	@SuppressWarnings({"unchecked", "null"})
	@Override
	public boolean init(final Expression<?>[] exprs, final int matchedPattern, final Kleenean isDelayed, final ParseResult parseResult) {

		open = matchedPattern == 0;

		if (parseResult.mark >= 5) {
			inventoryType = InventoryType.DISPENSER;
		} else if (parseResult.mark == 4) {
			inventoryType = InventoryType.DROPPER;
		} else if (parseResult.mark == 3) {
			inventoryType = InventoryType.HOPPER;
		} else if (parseResult.mark == 2) {
			inventoryType = InventoryType.ANVIL;
		} else if (parseResult.mark == 1) {
			inventoryType = InventoryType.CHEST;
		} else if (parseResult.mark == 0) {
			inventoryType = InventoryType.WORKBENCH;
		}

		inventoryExpr = open ? exprs[0] : null;
		players = (Expression<Player>) exprs[exprs.length - 1];

		if (exprs[0] instanceof Literal<?> lit && lit.getSingle() instanceof InventoryType type && !inventoryType.isCreatable()) {
			Skript.error("Cannot create an inventory of type " + Classes.toString(type));
			return false;
		}

		return true;
	}

	@Override
	protected void execute(final Event event) {
		if (inventoryExpr != null) {
			Object object = inventoryExpr.getSingle(event);
			openForPlayers(event, object);
		} else {
			for (final Player player : players.getArray(event)) {
				if (open) {
					openInventoryType(player, inventoryType);
				} else {
					player.closeInventory();
				}
			}
		}
	}

	private void openForPlayers(Event event, Object target) {
		if (target == null)
			return;

		Player[] targetPlayers = this.players.getArray(event);

		try {
			if (target instanceof Inventory inventory) {
				for (Player p : targetPlayers)
					p.openInventory(inventory);
			} else if (target instanceof InventoryType type && type.isCreatable()) {
				for (Player p : targetPlayers)
					openInventoryType(p, type);
			}
		} catch (IllegalArgumentException ex) {
			Skript.error("You can't open a " + formatTargetName(target) + " inventory to a player.");
		}
	}

	@SuppressWarnings("UnstableApiUsage")
	private static void openInventoryType(Player player, InventoryType type) {

		if (HAS_MENU_TYPE) {
			// Even though the actual api was added in 1.21.1, back then there wasn't support for creating inventory views with a null title.
			// Fallback to the older Bukkit api is thus done to avoid errors.
			// see https://github.com/PaperMC/Paper/blob/fbea3cdc0caca69814e5ab68b981fa0bdbe5331d/paper-server/src/main/java/org/bukkit/craftbukkit/inventory/CraftMenuType.java
			// fixed here https://github.com/PaperMC/Paper/blob/8eb8e44ac32a99f53da7af50e800ac8831030580/paper-server/src/main/java/org/bukkit/craftbukkit/inventory/CraftMenuType.java
			if (Skript.getMinecraftVersion().isLargerThan(new Version(1, 21, 3))) {
				if (type.getMenuType() != null) {
					player.openInventory(type.getMenuType().create(player, null));
					return;
				}
			}
		}

		player.openInventory(Bukkit.createInventory(null, type));
	}

	private static String formatTargetName(Object target) {
		if (target instanceof Inventory inventory)
			return inventory.getType().name().toLowerCase(Locale.ENGLISH).replace("_", "");

		if (target instanceof InventoryType inventoryType)
			return inventoryType.name().toLowerCase(Locale.ENGLISH).replace("_", "");

		return "unknown";
	}


	@Override
	public String toString(final @Nullable Event e, final boolean debug) {
		return (open
			? "open " + (inventoryExpr != null ? inventoryExpr.toString(e, debug) : "crafting table") + " to "
			: "close inventory view of ") + players.toString(e, debug);
	}
}