/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.modules.mail.events;

import io.github.nucleuspowered.nucleus.api.module.mail.event.NucleusSendMailEvent;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.impl.AbstractEvent;
import java.util.Optional;

import javax.annotation.Nullable;

public class InternalNucleusSendMailEvent extends AbstractEvent implements NucleusSendMailEvent {

    @Nullable private final User from;
    private final User to;
    private final String message;
    private final Cause cause;
    private boolean cancelled = false;

    public InternalNucleusSendMailEvent(@Nullable final User from, final User to, final String message) {
        this.cause = CauseStackHelper.createCause(from == null ? Sponge.getServer().getConsole() : from);
        this.from = from;
        this.to = to;
        this.message = message;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(final boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public Cause getCause() {
        return this.cause;
    }

    @Override
    public Optional<User> getSender() {
        return Optional.ofNullable(this.from);
    }

    @Override
    public User getRecipient() {
        return this.to;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}