package com.github.riku32.discordlink.core.platform.command;

import com.github.riku32.discordlink.core.platform.PlatformPlayer;
import com.github.riku32.discordlink.core.platform.command.annotation.Choice;
import com.github.riku32.discordlink.core.platform.command.annotation.Command;
import com.github.riku32.discordlink.core.platform.command.annotation.Default;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

public class CompiledCommand {
    private final CommandData baseCommand;
    private final Set<CommandData> subCommands;

    private final static List<Class<?>> ALLOWED_ARGS = Arrays.asList(
            String.class,
            boolean.class,
            PlatformPlayer.class
    );

    public CompiledCommand(Object command) throws CommandCompileException {
        Collection<Method> defaultHandlers = Arrays.stream(command.getClass().getDeclaredMethods())
                .peek(m -> m.setAccessible(true))
                .filter(m -> m.isAnnotationPresent(Default.class))
                .collect(Collectors.toUnmodifiableSet());

        if (defaultHandlers.size() != 1)
            throw new CommandCompileException("Each command must have exactly one Default handler");

        Method baseMethod = defaultHandlers.iterator().next();
        baseCommand = new CommandData(command, baseMethod, true, getArguments(baseMethod));

        if (!baseMethod.getParameterTypes()[0].equals(CommandSender.class))
            throw new CommandCompileException("Command handler's first argument must be a CommandSender");

        subCommands = Arrays.stream(command.getClass().getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Command.class))
                .map(m -> new CommandData(command, m, false, getArguments(m)))
                .collect(Collectors.toUnmodifiableSet());

        for (CommandData subCommand : subCommands) {
            if (!subCommand.getMethod().getParameterTypes()[0].equals(CommandSender.class))
                throw new CommandCompileException("Command handler's first argument must be a CommandSender");

            for (int i = 1; i < subCommand.getMethod().getParameters().length; i++) {
                Parameter parameter = subCommand.getMethod().getParameters()[i];

                if (!ALLOWED_ARGS.contains(parameter.getType()))
                    throw new CommandCompileException(String.format("Invalid argument type %s was provided", parameter.getType().getName()));

                if (parameter.getType() != String.class && parameter.isAnnotationPresent(Choice.class))
                    throw new CommandCompileException("Only a String argument can be annotated with Choice");
            }

            subCommand.getMethod().setAccessible(true);
        }
    }

    private List<ArgumentData> getArguments(Method method) {
        return Arrays.stream(method.getParameters())
                .skip(1)
                .map(parameter -> new ArgumentData(parameter.getType(), parameter.getName(),
                        parameter.isAnnotationPresent(Choice.class) ? parameter.getAnnotation(Choice.class).value() : null))
                .collect(Collectors.toUnmodifiableList());
    }

    public CommandData getBaseCommand() {
        return baseCommand;
    }

    public Set<CommandData> getSubCommands() {
        return subCommands;
    }
}
