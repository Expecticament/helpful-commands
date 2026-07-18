package com.expecticament.helpfulcommands.command.world;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.manager.ConfigManager;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.translation.ComponentBuilder;
import com.expecticament.helpfulcommands.manager.translation.TranslationManager;
import com.expecticament.helpfulcommands.permission.ModPermissions;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.expecticament.helpfulcommands.util.PermissionsUtil;
import com.expecticament.helpfulcommands.util.StylingUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

public class KillitemsCommand extends HelpfulCommandsCommand {
    private static final Dynamic2CommandExceptionType RANGE_CONFIG_VALUE_EXCEEDED = new Dynamic2CommandExceptionType((src, maxRange) ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpfulcommands.killitems.range_config_value_exceeded", Component.literal(String.valueOf(maxRange)).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).build()
    );

    public KillitemsCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .then(Commands.argument("range_cubic", IntegerArgumentType.integer(1))
                        .then(Commands.argument("filter", ItemPredicateArgument.itemPredicate(buildContext))
                                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "range_cubic"), ItemPredicateArgument.getItemPredicate(ctx, "filter")))
                        )
                        .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "range_cubic")))
                )
                .executes(this::execute)
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_KILLITEMS);
    }

    private int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return execute(ctx, 0, null);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, int range) throws CommandSyntaxException {
        return execute(ctx, range, null);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, int range, Predicate<ItemStack> itemPredicate) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        int maxRange = PermissionsUtil.getMetaOrElseConfigValue(src, ConfigManager.CONFIG_FIELD.KILLITEMS_MAX_RANGE);
        if (range < 1) {
            range = maxRange;
        } else if (range > maxRange){
            throw RANGE_CONFIG_VALUE_EXCEEDED.create(src, maxRange);
        }

        Vec3 center = src.getPosition();
        ServerLevel level = src.getLevel();
        AABB aabb = new AABB(center.x - range, center.y - range, center.z - range, center.x + range, center.y + range, center.z + range);

        List<ItemEntity> itemEntities = level.getEntitiesOfClass(ItemEntity.class, aabb, itemEntity -> itemPredicate == null || itemPredicate.test(itemEntity.getItem()));
        boolean multiple = false;
        for (ItemEntity itemEntity : itemEntities) {
            if (!multiple && itemEntity.getItem().getCount() > 1) {
                multiple = true;
            }
            itemEntity.discard();
        }

        if (itemEntities.isEmpty()) {
            throw NO_ITEMS_FOUND.create(src);
        }

        ComponentBuilder componentBuilder = new ComponentBuilder(src);
        componentBuilder.appendTranslatable("commands.helpfulcommands.killitems", StylingUtil.getAffectedEntitiesNumberText(itemEntities), Component.literal(TranslationManager.translate(src, "commands.helpfulcommands.killitems." + ((itemEntities.size() == 1 && !multiple) ? "single" : "multiple"))));
        componentBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(componentBuilder::build, true);

        return itemEntities.size();
    }
}
