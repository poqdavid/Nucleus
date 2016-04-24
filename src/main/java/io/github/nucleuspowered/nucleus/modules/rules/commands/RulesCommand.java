/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.modules.rules.commands;

import com.google.inject.Inject;
import io.github.nucleuspowered.nucleus.Util;
import io.github.nucleuspowered.nucleus.internal.annotations.*;
import io.github.nucleuspowered.nucleus.internal.command.CommandBase;
import io.github.nucleuspowered.nucleus.internal.permissions.SuggestedLevel;
import io.github.nucleuspowered.nucleus.modules.rules.config.RulesConfigAdapter;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.service.pagination.PaginationService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.text.MessageFormat;
import java.util.List;
import java.util.stream.Collectors;

@Permissions(suggestedLevel = SuggestedLevel.USER)
@RunAsync
@RegisterCommand("rules")
@NoWarmup
@NoCooldown
@NoCost
public class RulesCommand extends CommandBase<CommandSource> {

    @Inject private RulesConfigAdapter rca;

    @Override
    public CommandResult executeCommand(CommandSource src, CommandContext args) throws Exception {

        List<String> r = rca.getNodeOrDefault().getRuleSet();
        if (r.isEmpty()) {
            src.sendMessage(Util.getTextMessageWithFormat("command.rules.empty"));
            return CommandResult.empty();
        }

        // Number the rules.
        for (int i = 0; i < r.size(); i++) {
            r.set(i, MessageFormat.format("&a{0}: &r{1}", String.valueOf(i + 1), r.get(i)));
        }

        List<Text> rules = r.stream()
                .map(TextSerializers.FORMATTING_CODE::deserialize).collect(Collectors.toList());
        Sponge.getServiceManager().provideUnchecked(PaginationService.class).builder().contents(rules).padding(Text.of(TextColors.GREEN, "-"))
                .title(Util.getTextMessageWithFormat("command.rules.list.header"))
                .sendTo(src);

        return CommandResult.success();
    }
}
