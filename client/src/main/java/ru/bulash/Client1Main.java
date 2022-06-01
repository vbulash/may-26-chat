package ru.bulash;

public class Client1Main {
	public static void main(String[] args) {
		Thread client1 = new Thread(
				new Client(
						new User("vbulash", "1")
				)
		);
		client1.start();
	}
}
