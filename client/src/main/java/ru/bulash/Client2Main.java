package ru.bulash;

public class Client2Main {
	public static void main(String[] args) {
		Thread client2 = new Thread(
				new Client(
						new User("nbulash", "2")
				)
		);
		client2.start();
	}
}
