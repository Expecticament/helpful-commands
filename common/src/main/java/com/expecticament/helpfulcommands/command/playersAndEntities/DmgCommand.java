package com.expecticament.helpfulcommands.command.playersAndEntities;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.PermissionHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.permission.ModPermissions;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;

public class DmgCommand extends HelpfulCommandsCommand {
    private static final SimpleCommandExceptionType ERROR_INVULNERABLE = new SimpleCommandExceptionType(Component.translatable("commands.damage.invulnerable"));

    public DmgCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .then(Commands.argument("entities", EntityArgument.entities())
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0f))
                                .executes(context -> execute(context, EntityArgument.getEntities(context, "entities"), FloatArgumentType.getFloat(context, "amount"), context.getSource().getLevel().damageSources().generic()))
                                .then(Commands.argument("damage_type", ResourceArgument.resource(buildContext, Registries.DAMAGE_TYPE))
                                        .executes(context -> execute(context, EntityArgument.getEntities(context, "entities"), FloatArgumentType.getFloat(context, "amount"), new DamageSource(ResourceArgument.getResource(context, "damage_type", Registries.DAMAGE_TYPE))))
                                        .then(Commands.literal("at")
                                                .then(Commands.argument("location", Vec3Argument.vec3())
                                                        .executes(context -> execute(context, EntityArgument.getEntities(context, "entities"), FloatArgumentType.getFloat(context, "amount"), new DamageSource(ResourceArgument.getResource(context, "damage_type", Registries.DAMAGE_TYPE), Vec3Argument.getVec3(context, "location")))))
                                        )
                                        .then(Commands.literal("by")
                                                .then(Commands.argument("entity", EntityArgument.entity())
                                                        .executes(context -> execute(context, EntityArgument.getEntities(context, "entities"), FloatArgumentType.getFloat(context, "amount"), new DamageSource(ResourceArgument.getResource(context, "damage_type", Registries.DAMAGE_TYPE), EntityArgument.getEntity(context, "entity"))))
                                                        .then(Commands.literal("from")
                                                                .then(Commands.argument("cause", EntityArgument.entity())
                                                                        .executes(context -> execute(context, EntityArgument.getEntities(context, "entities"), FloatArgumentType.getFloat(context, "amount"), new DamageSource(ResourceArgument.getResource(context, "damage_type", Registries.DAMAGE_TYPE), EntityArgument.getEntity(context, "entity"), EntityArgument.getEntity(context, "cause"))))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionHelper.hasPermission(source, ModPermissions.Permission.COMMAND_DMG);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities, float amount, DamageSource damageSource) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        if(!src.isPlayer() && entities == null){
            throw CommandSourceStack.ERROR_NOT_PLAYER.create();
        }

        ServerLevel serverLevel = src.getLevel();

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        TranslationManager.TextBuilder textBuilder = new TranslationManager.TextBuilder(src);
        textBuilder.setStyle(textStyles.getSuccess());

        if (entities.size() == 1) {
            Entity entity = entities.iterator().next();
            if (entity.hurtServer(serverLevel, damageSource, amount)) {
                textBuilder.appendTranslatable("commands.helpful_commands.dmg.other.single", Component.literal(String.valueOf(amount)).setStyle(textStyles.getPrimary()), StylingHelper.getAffectedEntityNameText(entity));
                src.sendSuccess(textBuilder::getComponent, true);
                return Command.SINGLE_SUCCESS;
            } else {
                throw ERROR_INVULNERABLE.create();
            }
        }

        ArrayList<Entity> affected = new ArrayList<>();
        for(Entity entity : entities) {
            if (entity.hurtServer(serverLevel, damageSource, amount)) {
                affected.add(entity);
            }
        }

        if(affected.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        }

        textBuilder.appendTranslatable("commands.helpful_commands.dmg.other.multiple", Component.literal(String.valueOf(amount)).setStyle(textStyles.getPrimary()), StylingHelper.getAffectedEntitiesNumberText(affected));

        src.sendSuccess(textBuilder::getComponent, true);

        return affected.size();
    }
}
