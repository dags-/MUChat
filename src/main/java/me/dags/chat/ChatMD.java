package me.dags.chat;

import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author dags <dags@dags.me>
 */
@Plugin(id = "chatmd", name = "ChatMD", version = "1.0", description = ".")
public class ChatMD {

    private static final String HEADER_FORMAT = "{:prefix} {header:name}: ";
    private static final String BODY_FORMAT = "{body:message}";

    private final ConfigurationLoader<CommentedConfigurationNode> loader;
    private JoinListener joinListener;
    private MessageListener messageListener;
    private CommentedConfigurationNode config;

    @Inject
    public ChatMD(@DefaultConfig(sharedRoot = false) ConfigurationLoader<CommentedConfigurationNode> loader) {
        this.loader = loader;
        this.config = loader.createEmptyNode();
        loadConfig();
    }

    @Listener
    public void init(GameInitializationEvent event) {
        reloadFormats();
        reloadOptions();
        saveConfig();

        CommandSpec reload = CommandSpec.builder().permission("chatmd.command.reload").executor((src, args) -> {
            src.sendMessage(Text.of("Reloading..."));
            loadConfig();
            reloadFormats();
            reloadOptions();
            return CommandResult.success();
        }).build();

        CommandSpec main = CommandSpec.builder().child(reload, "reload").build();
        Sponge.getCommandManager().register(this, main, "chatmd");
    }

    private synchronized void reloadFormats() {
        ConfigurationNode formats = config.getNode("format");
        String header = getOrInsert(formats, "header", HEADER_FORMAT);
        String body = getOrInsert(formats, "body", BODY_FORMAT);
        MessageListener listener = new MessageListener(header, body);
        messageListener = registerListener(messageListener, listener);
    }

    private synchronized void reloadOptions() {
        ConfigurationNode options = config.getNode("options");
        ChatOptions defaultOptions = new ChatOptions("default", options.getNode("default"));
        List<ChatOptions> allOptions = new ArrayList<>();
        Map<?, ? extends ConfigurationNode> children = options.getChildrenMap();
        for (Map.Entry<?, ? extends ConfigurationNode> child : children.entrySet()) {
            String id = child.getKey().toString();
            ConfigurationNode node = child.getValue();
            allOptions.add(new ChatOptions(id, node));
        }
        JoinListener listener = new JoinListener(defaultOptions, allOptions);
        joinListener = registerListener(joinListener, listener);
    }

    private <T> T registerListener(T current, T next) {
        if (current != null) {
            Sponge.getEventManager().unregisterListeners(current);
        }
        Sponge.getEventManager().registerListeners(this, next);
        return next;
    }

    private void loadConfig() {
        try {
            this.config = loader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        try {
            loader.save(config);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T getOrInsert(ConfigurationNode parent, String key, T def) {
        ConfigurationNode node = parent.getNode(key);
        if (node.isVirtual()) {
            node.setValue(def);
            return def;
        }
        return (T) node.getValue();
    }
}
