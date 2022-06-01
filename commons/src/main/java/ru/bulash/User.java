package ru.bulash;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class User {
	private String nickname;
	private String password;

	public String getNickname() {
		return nickname;
	}

	public String getPassword() {
		return password;
	}

	public User(String nickname, String password) {
		this.nickname = nickname;
		this.password = password;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof User another)) {
			return false;
		}
		return this.nickname.equals(another.nickname) && this.password.equals(another.password);
	}
}
