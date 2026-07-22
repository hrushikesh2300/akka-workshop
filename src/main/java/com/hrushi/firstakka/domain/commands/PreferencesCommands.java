package com.hrushi.firstakka.domain.commands;

public final class PreferencesCommands {

    public record SetPreferences(
        String theme,
        String locale,
        boolean marketingOptIn
    ) {}
}
