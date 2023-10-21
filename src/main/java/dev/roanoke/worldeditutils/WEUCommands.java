package dev.roanoke.worldeditutils;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class WEUCommands {
    public WEUCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    literal("schempaste")
                            .requires(Permissions.require("worleditutils.schempaste", 4))
                            .then(argument("schematic_name", StringArgumentType.string())
                                    .suggests(this::suggestSchematics)
                                    .then(argument("world", IdentifierArgumentType.identifier())
                                            .suggests(this::suggestWorlds)
                                            .then(argument("x", DoubleArgumentType.doubleArg())
                                                    .then(argument("y", DoubleArgumentType.doubleArg())
                                                            .then(argument("z", DoubleArgumentType.doubleArg())
                                                                    .executes(this::executeSchemPaste)))))));
        });
    }

    private CompletableFuture<Suggestions> suggestWorlds(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        MinecraftServer server = ctx.getSource().getServer();

        return CommandSource.suggestMatching(StreamSupport.stream(server.getWorlds().spliterator(), false)
                .map(serverWorld -> serverWorld.getRegistryKey().getValue().toString()), builder);
    }

    private CompletableFuture<Suggestions> suggestSchematics(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        Path schematicsPath = FabricLoader.getInstance().getConfigDir().resolve("worldedit/schematics/");

        File directory = schematicsPath.toFile();
        Stream<String> fileNamesStream = Stream.empty();
        if (directory.exists() && directory.isDirectory()) {
            fileNamesStream = Stream.of(directory.listFiles())
                    .filter(File::isFile)
                    .map(File::getName);
        } else {
            return CommandSource.suggestMatching(new String[]{"NOSCHEMS"}, builder);
        }

        return CommandSource.suggestMatching(fileNamesStream, builder);
    }

    private int executeSchemPaste(CommandContext<ServerCommandSource> ctx) {
        File file = FabricLoader.getInstance().getConfigDir().resolve("worldedit/schematics/" + StringArgumentType.getString(ctx, "schematic_name")).toFile();
        if (!file.exists()) {
            ctx.getSource().sendMessage(Text.literal("The file '" + StringArgumentType.getString(ctx, "schematic_name" + "' does not exist in the WorldEdit Schematics Folder!")));
            return 1;
        }

        ServerWorld world = ctx.getSource().getServer().getWorld(RegistryKey.of(
                RegistryKeys.WORLD,
                IdentifierArgumentType.getIdentifier(ctx, "world")
        ));

        if (world == null) {
            ctx.getSource().sendMessage(Text.literal("Failed to get Server World from Identifier '" + StringArgumentType.getString(ctx, "world") + "'"));
            return 1;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            ctx.getSource().sendMessage(Text.literal("WorldEdit failed to identify Clipboard Format for the provided schematic."));
            return 1;
        }

        Clipboard clipboard;
        try {
            clipboard = format.getReader(Files.newInputStream(file.toPath())).read();
        } catch (IOException e) {
            e.printStackTrace();
            ctx.getSource().sendMessage(Text.literal("Failed to read the file provided."));
            return 1;
        }

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(FabricAdapter.adapt(world))) {
            var operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(
                            BlockVector3.at(
                                    DoubleArgumentType.getDouble(ctx, "x"),
                                    DoubleArgumentType.getDouble(ctx, "y"),
                                    DoubleArgumentType.getDouble(ctx, "z")
                                    )
                    )
                    .build();
            try {
                Operations.complete(operation);
            } catch (WorldEditException e) {
                e.printStackTrace();
                ctx.getSource().sendMessage(Text.literal("WorldEditException: Check Console for details"));
            }
        }

        return 1;
    }

}
