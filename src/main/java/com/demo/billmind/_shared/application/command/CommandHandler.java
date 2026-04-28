package com.demo.billmind._shared.application.command;

public interface CommandHandler<C extends Command> {
    void handle(C command);
    Class<C> commandType();
}
