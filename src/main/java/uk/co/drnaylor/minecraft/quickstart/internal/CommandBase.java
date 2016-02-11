package uk.co.drnaylor.minecraft.quickstart.internal;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.commented.SimpleCommentedConfigurationNode;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.*;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.source.CommandBlockSource;
import org.spongepowered.api.command.source.ConsoleSource;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.TextMessageException;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import uk.co.drnaylor.minecraft.quickstart.QuickStart;
import uk.co.drnaylor.minecraft.quickstart.Util;
import uk.co.drnaylor.minecraft.quickstart.argumentparsers.NoCostArgument;
import uk.co.drnaylor.minecraft.quickstart.internal.annotations.*;
import uk.co.drnaylor.minecraft.quickstart.internal.services.WarmupManager;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

public abstract class CommandBase<T extends CommandSource> implements CommandExecutor {

    private final boolean isAsync = this.getClass().getAnnotation(RunAsync.class) != null;
    private final Set<String> additionalPermissions;
    private final Set<String> cooldown;
    private final Set<String> warmup;
    private final Set<String> cost;

    private final Map<UUID, Instant> cooldownStore = Maps.newHashMap();
    protected final PermissionUtil permissions;
    private final Class<T> sourceType;
    private final boolean bypassWarmup;
    private final boolean bypassCooldown;
    private final boolean bypassCost;
    private final String configSection;
    private final boolean generateDefaults;

    @Inject protected QuickStart plugin;
    @Inject private WarmupManager warmupService;

    @SuppressWarnings("unchecked")
    protected CommandBase() {
        // I hate type erasure - it leads to a hack like this. Admittedly, I could've just created a subclass that does
        // the same thing, but I like to beat the system! :)
        //
        // This code reflectively looks for methods called "executeCommand", which is defined in this class. However,
        // due to type erasure, if a generic type is specified, there are two "executeCommand" methods, one that satisfies
        // this abstract class (with the CommandSource argument) and a second method that fulfils the generic type T.
        //
        // Thus, we need to check that there is a method called executeCommand that has a more restrictive argument than
        // "CommandSource" in order to check for the generic type.
        //
        // This allows us to then have code that filters out non-Players by simply specifying the generic type "Player".
        //
        // The provided stream filters out the standard executeCommand method, and checks to see if there is a second that makes
        // use of the generic parameter.
        Optional<Method> me = Arrays.asList(getClass().getMethods()).stream().filter(x -> x.getName().equals("executeCommand") &&
                x.getParameterTypes().length == 2 &&
                x.getParameterTypes()[1].isAssignableFrom(CommandContext.class) &&
                !x.getParameterTypes()[0].equals(CommandSource.class)).findFirst();

        // If there is a second executeCommand method, then we know that's the type that we need and we can do our source
        // checks against it accordingly.
        if (me.isPresent()) {
            sourceType = (Class<T>) (me.get().getParameterTypes()[0]);
        } else {
            sourceType = (Class<T>) CommandSource.class;
        }

        // For these flags, we simply need to get whether the annotation was declared. If they were not, we simply get back
        // a null - so the check is based around that.
        bypassWarmup = this.getClass().getAnnotation(NoWarmup.class) != null;
        bypassCooldown = this.getClass().getAnnotation(NoCooldown.class) != null;
        bypassCost = this.getClass().getAnnotation(NoCost.class) != null;

        // The Permissions annotation provides the backbone of the permissions system for the commands.
        // The standard permisson is based on the getAliases command, specifically, the first argument in the
        // returned array, such that the permission it will generated if this annotation is defined is:
        //
        // quickstart.(primaryalias).base
        //
        // Adding a "root" and/or "sub" string will generate:
        //
        // quickstart.(root).(primaryalias).(sub).base
        //
        // For warmup, cooldown and cost exemption, replace base with:
        //
        // exempt.(cooldown|warmup|cost)
        //
        // By default, the permission "quickstart.admin" also gets permission to run and bypass all warmups,
        // cooldowns and costs, but this can be turned off in the annotation.
        Permissions op = this.getClass().getAnnotation(Permissions.class);
        additionalPermissions = Sets.newHashSet();
        cooldown = Sets.newHashSet();
        warmup = Sets.newHashSet();
        cost = Sets.newHashSet();

        if (op == null) {
            permissions = null;
        } else {
            permissions = new PermissionUtil(op, getAliases()[0]);
            additionalPermissions.addAll(permissions.getBasePermissions());
            cooldown.addAll(permissions.getCooldownPermissions());
            warmup.addAll(permissions.getWarmupPermissions());
            cost.addAll(permissions.getCostPermissions());
        }

        ConfigCommandAlias cca = this.getClass().getAnnotation(ConfigCommandAlias.class);
        configSection = cca == null ? getAliases()[0].toLowerCase() : cca.value().toLowerCase();
        generateDefaults = cca == null || cca.generate();
    }

    // Abstract functions - for implementation.

    /**
     * Returns a {@link CommandSpec} that allows this command to be registered.
     *
     * @return The {@link CommandSpec}
     */
    public abstract CommandSpec createSpec();

    /**
     * Gets the aliases for the command. The first alias will be the primary alias within QuickStart.
     *
     * @return An array of aliases.
     */
    public abstract String[] getAliases();

    /**
     * Functionally similar to {@link CommandExecutor#execute(CommandSource, CommandContext)}, this contains logic that
     * actually executes the command.
     *
     * <p>
     *     Note that the {@link CommandResult} is important here. A success is treated differently to a non-success!
     * </p>
     *
     * @param src The executor of the command.
     * @param args The arguments for the command.
     * @return The {@link CommandResult}
     * @throws Exception If thrown, {@link TextMessageException#getText()} or {@link Exception#getMessage()} will be
     *                   sent to the user.
     */
    public abstract CommandResult executeCommand(T src, CommandContext args) throws Exception;

    // -------------------------------------
    // Metadata
    // -------------------------------------

    public final boolean mergeDefaults() {
        return generateDefaults;
    }

    public CommentedConfigurationNode getDefaults() {
        CommentedConfigurationNode n = SimpleCommentedConfigurationNode.root();
        n.getNode("enabled").setComment(Util.messageBundle.getString("config.enabled")).setValue(true);

        if (!bypassCooldown) {
            n.getNode("cooldown").setComment(Util.messageBundle.getString("config.cooldown")).setValue(0);
        }

        if (!bypassWarmup) {
            n.getNode("warmup").setComment(Util.messageBundle.getString("config.warmup")).setValue(0);
        }

        if (!bypassCost) {
            n.getNode("cost").setComment(Util.messageBundle.getString("config.cost")).setValue(0);
        }

        return n;
    }

    public final Set<String> getCommandPermissions() {
        return ImmutableSet.copyOf(additionalPermissions);
    }

    public final Set<String> getWarmupExemptPermissions() {
        return ImmutableSet.copyOf(warmup);
    }

    public final Set<String> getCooldownExemptPermissions() {
        return ImmutableSet.copyOf(cooldown);
    }

    public final Set<String> getCostExemptPermissions() {
        return ImmutableSet.copyOf(cost);
    }

    // -------------------------------------
    // Command Execution
    // -------------------------------------

    /**
     * Runs any checks that need to occur before the warmup, cooldown and cost checks.
     *
     * @param source The source of the command.
     * @param args The arguments.
     * @return Whether to continue or not.
     */
    protected ContinueMode preProcessChecks(T source, CommandContext args) throws Exception {
        return ContinueMode.CONTINUE;
    }

    @Override
    @NonnullByDefault
    public final CommandResult execute(CommandSource source, CommandContext args) throws CommandException {
        // If the implementing class has defined a generic parameter, then check the source type.
        if (!checkSourceType(source)) {
            return CommandResult.empty();
        }

        // Cast as required.
        @SuppressWarnings("unchecked") T src = (T)source;

        // If they don't match ANY permission, throw 'em.
        if (!additionalPermissions.isEmpty() && !additionalPermissions.stream().anyMatch(src::hasPermission)) {
            throw new CommandPermissionException();
        }

        try {
            ContinueMode mode = preProcessChecks(src, args);
            if (!mode.cont) {
                return mode.returnType;
            }
        } catch (Exception e) {
            // If it doesn't, just tell the user something went wrong.
            src.sendMessage(Text.of(QuickStart.ERROR_MESSAGE_PREFIX, TextColors.RED, Util.messageBundle.getString("command.error")));
            e.printStackTrace();
            return CommandResult.empty();
        }

        if (src instanceof Player) {
            ContinueMode cm = runChecks((Player)src, args);

            if (!cm.cont) {
                return cm.returnType;
            }
        }

        // If we're running async...
        if (isAsync) {
            // Create an executor that runs the command async.
            plugin.getLogger().debug("Running " + this.getClass().getName() + " in async mode.");
            Sponge.getScheduler().createAsyncExecutor(plugin).execute(() -> startExecute(src, args));

            // Tell Sponge we're done.
            return CommandResult.success();
        }

        // Run the command sync.
        return startExecute(src, args);
    }

    private CommandResult startExecute(T src, CommandContext args) {
        CommandResult cr;
        try {
            // Execute the command in the specific executor.
            cr = executeCommand(src, args);
        } catch (TextMessageException e) {
            // If the exception contains a text object, render it like so...
            src.sendMessage(Text.of(QuickStart.ERROR_MESSAGE_PREFIX, e.getText()));
            e.printStackTrace();
            cr = CommandResult.empty();
        } catch (Exception e) {
            // If it doesn't, just tell the user something went wrong.
            src.sendMessage(Text.of(QuickStart.ERROR_MESSAGE_PREFIX, TextColors.RED, Util.messageBundle.getString("command.error")));
            e.printStackTrace();
            cr = CommandResult.empty();
        }

        if (src instanceof Player) {
            // If the player is subject to cooling down, apply the cooldown.
            final Player p = (Player)src;

            if (cr.getSuccessCount().orElse(0) > 0) {
                setCooldown(p);
            } else {
                // For the tests, keep this here so we can skip the hard to test code below.
                final double cost = getCost(p, args);
                if (cost > 0) {
                    Sponge.getScheduler().createSyncExecutor(plugin).execute(() -> plugin.getEconHelper().depositInPlayer(p, cost));
                }
            }
        }

        return cr;
    }

    // -------------------------------------
    // Source Type
    // -------------------------------------
    private boolean checkSourceType(CommandSource source) {
        if (sourceType.equals(Player.class) && !(source instanceof Player)) {
            source.sendMessage(Text.of(TextColors.RED, Util.messageBundle.getString("command.playeronly")));
            return false;
        } else if (sourceType.equals(ConsoleSource.class) && !(source instanceof Player)) {
            source.sendMessage(Text.of(TextColors.RED, Util.messageBundle.getString("command.consoleonly")));
            return false;
        } else if (sourceType.equals(CommandBlockSource.class) && !(source instanceof CommandBlockSource)) {
            source.sendMessage(Text.of(TextColors.RED, Util.messageBundle.getString("command.commandblockonly")));
            return false;
        }

        return true;
    }

    // -------------------------------------
    // Player Checks
    // -------------------------------------
    private ContinueMode runChecks(Player src, CommandContext args) {
        // Cooldown, cost, warmup.
        ContinueMode m = checkCooldown(src);
        if (!m.cont) {
            return m;
        }

        m = applyCost(src, args);
        if (!m.cont) {
            return m;
        }

        return setupWarmup(src, args);
    }

    // -------------------------------------
    // Warmups
    // -------------------------------------
    @SuppressWarnings("unchecked")
    private ContinueMode setupWarmup(final Player src, CommandContext args) {
        if (bypassWarmup || warmup.isEmpty() || warmup.stream().anyMatch(src::hasPermission)) {
            return ContinueMode.CONTINUE;
        }

        // Get the warmup time.
        int warmupTime = plugin.getConfig(ConfigMap.COMMANDS_CONFIG).get().getCommandNode(configSection).getNode("warmup").getInt();
        if (warmupTime <= 0) {
            return ContinueMode.CONTINUE;
        }

        // We create a task that executes the command at a later time. Because we already know we have permission,
        // we can skip those checks.
        Task.Builder tb = Sponge.getScheduler().createTaskBuilder().delay(warmupTime, TimeUnit.SECONDS).execute(
            new CostCancellableTask(plugin, src, getCost(src, args)) {
                @Override
                public void accept(Task task) {
                    src.sendMessage(Text.builder(Util.messageBundle.getString("warmup.end")).color(TextColors.YELLOW).build());
                    warmupService.removeWarmup(src.getUniqueId());
                    startExecute((T)src, args);
                }
        }).name("Command Warmup - " + src.getName());

        // Run an async command async, of course!
        if (isAsync) {
            tb.async();
        }

        // Add the warmup to the service so we can cancel it if we need to.
        warmupService.addWarmup(src.getUniqueId(), tb.submit(plugin));

        // Tell the user we're warming up.
        src.sendMessage(Text.builder(MessageFormat.format(Util.messageBundle.getString("warmup.start"), Util.getTimeStringFromSeconds(warmupTime)))
                .color(TextColors.YELLOW).build());

        // Sponge should think the command was run successfully.
        return ContinueMode.STOP_SUCCESS;
    }

    // -------------------------------------
    // Cooldowns
    // -------------------------------------
    private ContinueMode checkCooldown(Player src) {
        // Remove any expired cooldowns.
        cleanCooldowns();

        // If they are still in there, then tell them they are still cooling down.
        if (!bypassCooldown && !cooldown.stream().anyMatch(src::hasPermission) && cooldownStore.containsKey(src.getUniqueId())) {
            Instant l = cooldownStore.get(src.getUniqueId());
            src.sendMessage(Text.builder(MessageFormat.format(Util.messageBundle.getString("cooldown.message"), Util.getTimeStringFromSeconds(l.until(Instant.now(), ChronoUnit.SECONDS))))
                    .color(TextColors.YELLOW).build());
            return ContinueMode.STOP;
        }

        return ContinueMode.CONTINUE;
    }

    private void setCooldown(Player src) {
        if (!cooldown.stream().anyMatch(src::hasPermission)) {
            // Get the cooldown time.
            int cooldownTime = plugin.getConfig(ConfigMap.COMMANDS_CONFIG).get().getCommandNode(configSection).getNode("cooldown").getInt();
            if (cooldownTime > 0) {
                // If there is a cooldown, add the cooldown to the list, with the end time as an Instant.
                cooldownStore.put(src.getUniqueId(), Instant.now().plus(cooldownTime, ChronoUnit.SECONDS));
            }
        }
    }

    private void cleanCooldowns() {
        cooldownStore.entrySet().stream().filter(k -> k.getValue().isAfter(Instant.now())).map(Map.Entry::getKey).forEach(cooldownStore::remove);
    }

    // -------------------------------------
    // Costs
    // -------------------------------------
    /**
     * Applies a cost to the user, if required.
     *
     * @param src The {@link CommandSource}
     */
    protected ContinueMode applyCost(Player src, CommandContext args) {
        double cost = getCost(src, args);
        if (cost == 0.) {
            return ContinueMode.CONTINUE;
        }

        if (!plugin.getEconHelper().withdrawFromPlayer(src, cost)) {
            return ContinueMode.STOP;
        }

        return ContinueMode.CONTINUE;
    }

    /**
     * Gets the cost for this command, or zero if the player does not have to pay.
     *
     * @param src The {@link CommandSource}
     * @return The cost.
     */
    protected double getCost(Player src, @Nullable CommandContext args) {
        boolean noCost = args != null && !args.<Boolean>getOne(NoCostArgument.NO_COST_ARGUMENT).orElse(false);

        // If the player or command itself is exempt, return a zero.
        if (bypassCost || noCost || cost.isEmpty() || cost.stream().anyMatch(src::hasPermission)) {
            return 0.;
        }

        // Return the cost if positive, else, zero.
        double cost = plugin.getConfig(ConfigMap.COMMANDS_CONFIG).get().getCommandNode(configSection).getNode("cost").getDouble(0.);
        if (cost <= 0.) {
            return 0.;
        }

        return cost;
    }

    @SafeVarargs
    protected final Map<List<String>, CommandCallable> createChildCommands(Class<? extends CommandBase>... bases) {
        Map<List<String>, CommandCallable> map = Maps.newHashMap();
        Arrays.asList(bases).forEach(cb -> {
            CommandBase c = plugin.getInjector().getInstance(cb);
            map.put(Arrays.asList(c.getAliases()), c.createSpec());
        });

        return map;
    }

    protected enum ContinueMode {
        /**
         * Continue executing the command.
         */
        CONTINUE(true, null),

        /**
         * Stop executing, but mark as success.
         */
        STOP_SUCCESS(false, CommandResult.success()),

        /**
         * Stop executing, mark as empty.
         */
        STOP(false, CommandResult.empty());

        final boolean cont;
        final CommandResult returnType;

        ContinueMode(boolean cont, CommandResult returnType) {
            this.cont = cont;
            this.returnType = returnType;
        }
    }
}
