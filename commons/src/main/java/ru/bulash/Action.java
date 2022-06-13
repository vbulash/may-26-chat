package ru.bulash;

import org.jetbrains.annotations.NotNull;

public class Action {
	private final String command;
	private final String[] data;

	public Action(@NotNull String message) {
		String[] items = message.split("\\|");
		this.command = items[0];
		this.data = new String[items.length - 1];
		System.arraycopy(items, 1, data, 0, items.length - 1);
	}

	public String getCommand() {
		return command;
	}

	public String[] getData() {
		return data;
	}
}
