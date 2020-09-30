/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.modules.admin.commands;

import io.github.nucleuspowered.nucleus.modules.admin.AdminPermissions;
import io.github.nucleuspowered.nucleus.scaffold.command.ICommandContext;
import io.github.nucleuspowered.nucleus.scaffold.command.ICommandExecutor;
import io.github.nucleuspowered.nucleus.scaffold.command.ICommandResult;
import io.github.nucleuspowered.nucleus.scaffold.command.NucleusParameters;
import io.github.nucleuspowered.nucleus.scaffold.command.annotation.Command;
import io.github.nucleuspowered.nucleus.services.INucleusServiceCollection;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandElement;
import org.spongepowered.api.text.serializer.TextSerializers;
import java.util.Optional;

@Command(aliases = {"stop"},
        basePermission = AdminPermissions.BASE_STOP,
        commandDescriptionKey = "stop")
public class StopCommand implements ICommandExecutor {

    @Override
    public CommandElement[] parameters(final INucleusServiceCollection serviceCollection) {
        return new CommandElement[] {
                NucleusParameters.OPTIONAL_MESSAGE
        };
    }

    @Override
    public ICommandResult execute(final ICommandContext context) {
        final Optional<String> opt = context.getOne(NucleusParameters.Keys.MESSAGE, String.class);
        if (opt.isPresent()) {
            Sponge.getServer().shutdown(TextSerializers.FORMATTING_CODE.deserialize(opt.get()));
        } else {
            Sponge.getServer().shutdown();
        }

        return context.successResult();
    }
}