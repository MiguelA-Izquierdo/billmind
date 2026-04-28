package com.demo.billmind._shared.application.command;

public interface CommandBus {
    <C extends Command> void dispatch(C command);
}
