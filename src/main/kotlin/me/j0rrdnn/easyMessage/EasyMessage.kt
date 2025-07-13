package me.j0rrdnn.easyMessage

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable

class EasyMessage : JavaPlugin(), Listener {

    private val playerBossBars = mutableMapOf<Player, BossBar>()
    private val announcementBossBars = mutableMapOf<Player, BossBar>()

    override fun onEnable() {
        logger.info("EasyMessage v1.0 has been loaded :3")
        logger.info("Welcome to EasyMessage!")

        // Create config folder and config.yml if they don't exist
        saveDefaultConfig()

        // Set default config values if they don't exist
        if (!config.contains("welcome-message")) {
            config.set("welcome-message", "&aWelcome to the server, {player}!")
        }
        if (!config.contains("leave-message")) {
            config.set("leave-message", "&e{player} &ahas left the server.")
        }
        if (!config.contains("announcement-message")) {
            config.set("announcement-message", "&6Welcome {player} to our amazing server!")
        }
        if (!config.contains("announcement-enabled")) {
            config.set("announcement-enabled", true)
        }
        if (!config.contains("announcement-duration")) {
            config.set("announcement-duration", 10) // seconds
        }
        if (!config.contains("enabled")) {
            config.set("enabled", true)
        }
        // New config options for announce command
        if (!config.contains("announce-duration")) {
            config.set("announce-duration", 5) // seconds
        }
        if (!config.contains("announce-sound")) {
            config.set("announce-sound", "ENTITY_EXPERIENCE_ORB_PICKUP")
        }
        if (!config.contains("announce-sound-enabled")) {
            config.set("announce-sound-enabled", true)
        }
        if (!config.contains("announce-bar-color")) {
            config.set("announce-bar-color", "RED")
        }
        saveConfig()

        // Register this class as the event listener
        server.pluginManager.registerEvents(this, this)

        // Register the command using Paper's method
        val emCommand = EMCommand(this)
        server.commandMap.register("easymessage", emCommand)
    }

    override fun onDisable() {
        // Remove all boss bars when plugin disables
        playerBossBars.values.forEach { bossBar ->
            bossBar.removeAll()
        }
        playerBossBars.clear()

        announcementBossBars.values.forEach { bossBar ->
            bossBar.removeAll()
        }
        announcementBossBars.clear()

        logger.info("Leaving EasyMessage...")
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val playerName = player.name

        // Check if welcome message is enabled
        if (!config.getBoolean("enabled", true)) {
            return
        }

        // Get the welcome message from config
        val welcomeMessageText = config.getString("welcome-message", "&aWelcome to the server, {player}!")

        // Replace {player} placeholder with actual player name
        val processedMessage = welcomeMessageText?.replace("{player}", playerName) ?: "&aWelcome to the server, $playerName!"

        // Parse the message with color codes using LegacyComponentSerializer
        val welcomeMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(processedMessage)

        player.sendMessage(welcomeMessage)

        // Show announcement boss bar if enabled
        if (config.getBoolean("announcement-enabled", true)) {
            val announcementText = config.getString("announcement-message", "&6Welcome {player} to our amazing server!")
            val processedAnnouncement = announcementText?.replace("{player}", playerName) ?: "&6Welcome $playerName to our amazing server!"

            showBossBar(player, processedAnnouncement)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val playerName = player.name

        // Check if welcome message is enabled
        if (!config.getBoolean("enabled", true)) {
            return
        }

        // Define leave message raw text
        var leaveMessageText = config.getString("leave-message", "&e{player} &ahas left the server.")

        // Replace {player} placeholder with actual player name
        val processedMessage = leaveMessageText?.replace("{player}", playerName) ?: "&e$playerName &ahas left the server."

        // Parse the message with color codes using LegacyComponentSerializer
        val leaveMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(processedMessage)

        player.sendMessage(leaveMessage)

        // Remove boss bars when player leaves
        playerBossBars[player]?.let { bossBar ->
            bossBar.removePlayer(player)
            bossBar.removeAll()
        }
        playerBossBars.remove(player)

        announcementBossBars[player]?.let { bossBar ->
            bossBar.removePlayer(player)
            bossBar.removeAll()
        }
        announcementBossBars.remove(player)
    }

    fun showBossBar(player: Player, message: String) {
        // Remove existing boss bar if present
        playerBossBars[player]?.let { existingBar ->
            existingBar.removePlayer(player)
            existingBar.removeAll()
        }

        // Create new boss bar using legacy color codes
        val bossBar = Bukkit.createBossBar(
            message.replace('&', '§'), // Convert & color codes to §
            BarColor.YELLOW,
            BarStyle.SOLID
        )

        // Show boss bar to player
        bossBar.addPlayer(player)
        bossBar.progress = 1.0
        bossBar.isVisible = true
        playerBossBars[player] = bossBar

        // Schedule removal after configured duration
        val duration = config.getInt("announcement-duration", 10)
        object : BukkitRunnable() {
            override fun run() {
                bossBar.removePlayer(player)
                bossBar.removeAll()
                playerBossBars.remove(player)
            }
        }.runTaskLater(this, (duration * 20).toLong()) // Convert seconds to ticks
    }

    fun announceToAll(message: String) {
        val onlinePlayers = Bukkit.getOnlinePlayers()
        if (onlinePlayers.isEmpty()) return

        // Get config values
        val duration = config.getInt("announce-duration", 5)
        val soundEnabled = config.getBoolean("announce-sound-enabled", true)
        val soundName = config.getString("announce-sound", "ENTITY_EXPERIENCE_ORB_PICKUP")
        val barColorName = config.getString("announce-bar-color", "RED")

        // Parse bar color
        val barColor = try {
            BarColor.valueOf(barColorName?.uppercase() ?: "RED")
        } catch (e: IllegalArgumentException) {
            BarColor.RED
        }

        // Parse sound
        @Suppress("DEPRECATION")
        val sound = try {
            Sound.valueOf(soundName?.uppercase() ?: "ENTITY_EXPERIENCE_ORB_PICKUP")
        } catch (e: IllegalArgumentException) {
            Sound.ENTITY_EXPERIENCE_ORB_PICKUP
        }

        // Create boss bar for announcement
        val bossBar = Bukkit.createBossBar(
            message.replace('&', '§'), // Convert & color codes to §
            barColor,
            BarStyle.SOLID
        )

        // Show to all online players
        onlinePlayers.forEach { player ->
            // Remove existing announcement boss bar if present
            announcementBossBars[player]?.let { existingBar ->
                existingBar.removePlayer(player)
                existingBar.removeAll()
            }

            // Add player to new boss bar
            bossBar.addPlayer(player)
            announcementBossBars[player] = bossBar

            // Play sound if enabled
            if (soundEnabled) {
                player.playSound(player.location, sound, 1.0f, 1.0f)
            }
        }

        bossBar.progress = 1.0
        bossBar.isVisible = true

        // Schedule removal after configured duration
        object : BukkitRunnable() {
            override fun run() {
                bossBar.removeAll()
                // Clear from tracking map
                announcementBossBars.values.removeAll { it == bossBar }
                announcementBossBars.entries.removeAll { it.value == bossBar }
            }
        }.runTaskLater(this, (duration * 20).toLong()) // Convert seconds to ticks
    }
}

// Custom command class for Paper plugins
class EMCommand(private val plugin: EasyMessage) : Command("em") {

    init {
        description = "EasyMessage main command"
        usage = "/em <help|info|reload|test|toggle|edit|announce>"
        permission = "easymessage.use"
        aliases = listOf("easymessage")
    }

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<String>): Boolean {
        when (args.getOrNull(0)?.lowercase()) {
            "help" -> {
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&6&lEasyMessage Commands:"))
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e/em help &7- Show this help message"))
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e/em info &7- Displays information about this plugin"))
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e/em reload &7- Reload the configuration"))
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e/em test &7- Test the welcome message"))
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e/em toggle &7- Enable/disable welcome messages"))
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e/em edit welcome <message> &7- Edit welcome message"))
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e/em edit announcements <message> &7- Edit announcement message"))
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e/em announce <message> &7- Announce message to all players"))
                return true
            }
            "info" -> {
                val infoMessage = "&6&lEasyMessage &7- Information\n&r\n&7&l| &6&lDevelopers&f: &7Jordan / @j0rrdnn\n&7&l| &6&lVersion&f: &71.0\n&7&l| &6&lGithub&f: &7github.com/j0rrdnn\n&7&l| &6&lDiscord&f: &7@j0rrdnn\n&r\n&7&oFound any bugs regarding this plugin? Contact me on Discord!"
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(infoMessage))
                return true
            }
            "announce" -> {
                if (!sender.hasPermission("easymessage.announce")) {
                    sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cYou don't have permission to use this command!"))
                    return true
                }
                if (args.size < 2) {
                    sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cUsage: /em announce <message>"))
                    return true
                }

                val message = args.drop(1).joinToString(" ")
                plugin.announceToAll(message)
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&aAnnouncement sent to all online players!"))
                return true
            }
            "reload" -> {
                if (!sender.hasPermission("easymessage.reload")) {
                    sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cYou don't have permission to use this command!"))
                    return true
                }
                plugin.reloadConfig()
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&aConfiguration reloaded successfully!"))
                return true
            }
            "test" -> {
                if (!sender.hasPermission("easymessage.test")) {
                    sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cYou don't have permission to use this command!"))
                    return true
                }
                if (sender !is Player) {
                    sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cThis command can only be used by players!"))
                    return true
                }

                // Test the welcome message
                val welcomeMessageText = plugin.config.getString("welcome-message", "&aWelcome to the server, {player}!")
                val processedMessage = welcomeMessageText?.replace("{player}", sender.name) ?: "&aWelcome to the server, ${sender.name}!"
                val welcomeMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(processedMessage)
                sender.sendMessage(welcomeMessage)

                // Test the announcement message
                if (plugin.config.getBoolean("announcement-enabled", true)) {
                    val announcementText = plugin.config.getString("announcement-message", "&6Welcome {player} to our amazing server!")
                    val processedAnnouncement = announcementText?.replace("{player}", sender.name) ?: "&6Welcome ${sender.name} to our amazing server!"

                    plugin.showBossBar(sender, processedAnnouncement)
                }
                return true
            }
            "toggle" -> {
                if (!sender.hasPermission("easymessage.toggle")) {
                    sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cYou don't have permission to use this command!"))
                    return true
                }
                val currentState = plugin.config.getBoolean("enabled", true)
                plugin.config.set("enabled", !currentState)
                plugin.saveConfig()

                val status = if (!currentState) "&aenabled" else "&cdisabled"
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&eWelcome messages are now $status&e!"))
                return true
            }
            "edit" -> {
                if (!sender.hasPermission("easymessage.edit")) {
                    sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cYou don't have permission to use this command!"))
                    return true
                }

                if (args.size < 3) {
                    sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cUsage: /em edit <text/announcements> <message>"))
                    return true
                }

                val editType = args[1].lowercase()
                val newMessage = args.drop(2).joinToString(" ")

                when (editType) {
                    "welcome" -> {
                        plugin.config.set("welcome-message", newMessage)
                        plugin.saveConfig()
                        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&aWelcome message updated successfully!"))
                        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&7New message: ${LegacyComponentSerializer.legacyAmpersand().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(newMessage))}"))
                    }
                    "leave" -> {
                        plugin.config.set("leave-message", newMessage)
                        plugin.saveConfig()
                        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&aLeave message updated successfully!"))
                        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&7New message: ${LegacyComponentSerializer.legacyAmpersand().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(newMessage))}"))
                    }
                    "announcements" -> {
                        plugin.config.set("announcement-message", newMessage)
                        plugin.saveConfig()
                        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&aAnnouncement message updated successfully!"))
                        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&7New message: ${LegacyComponentSerializer.legacyAmpersand().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(newMessage))}"))
                    }
                    else -> {
                        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cInvalid edit type! Use 'welcome', 'leave' or 'announcements'"))
                    }
                }
                return true
            }
            else -> {
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cUnknown command! Use &e/em help &cfor help."))
                return true
            }
        }
    }

    override fun tabComplete(sender: CommandSender, alias: String, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> {
                val subcommands = mutableListOf<String>()
                subcommands.add("help")
                subcommands.add("info")

                if (sender.hasPermission("easymessage.reload")) {
                    subcommands.add("reload")
                }
                if (sender.hasPermission("easymessage.test")) {
                    subcommands.add("test")
                }
                if (sender.hasPermission("easymessage.toggle")) {
                    subcommands.add("toggle")
                }
                if (sender.hasPermission("easymessage.edit")) {
                    subcommands.add("edit")
                }
                if (sender.hasPermission("easymessage.announce")) {
                    subcommands.add("announce")
                }

                subcommands.filter { it.startsWith(args[0].lowercase()) }
            }
            2 -> {
                if (args[0].equals("edit", ignoreCase = true) && sender.hasPermission("easymessage.edit")) {
                    listOf("welcome", "leave", "announcements").filter { it.startsWith(args[1].lowercase()) }
                } else if (args[0].equals("announce", ignoreCase = true) && sender.hasPermission("easymessage.announce")) {
                    listOf("Enter your announcement message here...")
                } else {
                    emptyList()
                }
            }
            3 -> {
                if (args[0].equals("edit", ignoreCase = true) && sender.hasPermission("easymessage.edit")) {
                    when (args[1].lowercase()) {
                        "welcome" -> listOf("Use {player} for player name placeholder")
                        "leave" -> listOf("Use {player} for player name placeholder")
                        "announcements" -> listOf("Use {player} for player name placeholder")
                        else -> emptyList()
                    }
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }
}