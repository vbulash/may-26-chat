package ru.bulash;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ClientProcess extends Thread {
	private final Socket clientSocket;
	private final Thread sendThread;
	private final Thread receiveThread;
	public PrintWriter out;
	private BufferedReader in;
	private Scanner sc;
	private User user = null;

	public ClientProcess(Socket socket) {
		this.clientSocket = socket;

		// Отправить сообщение с сервера / через сервер на клиент
		sendThread = new Thread(new Runnable() {
			String message;

			@Override
			public void run() {
				try {
					negotiate();
					while (processIsAlive()) {
						try {
							message = in.readLine();
							composeMessage(message);
						} catch (RuntimeException e) {
							closeClient();
						}
					}
				} catch (IOException exc) {
					System.out.println("Ошибка получения сообщения с клиента на сервер");
					exc.printStackTrace();
				}
			}
		});

		// Получить сообщение с клиента на сервер
		receiveThread = new Thread(new Runnable() {
			String message;

			@Override
			public void run() {
				while (processIsAlive()) {
					try {
						message = sc.nextLine();
						parseInput(message);
					} catch (Exception e) {
						//
					}
				}
			}
		});
	}

	@Override
	public void run() {
		try {
			this.out = new PrintWriter(clientSocket.getOutputStream());
			this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			this.sc = new Scanner(this.in);

			sendThread.start();
			receiveThread.start();
		} catch (IOException exc) {
			System.out.println("Ошибка старта / работы процесса поддержки клиента");
			exc.printStackTrace();
		}
	}

	private boolean parseInput(String message) {
		if (message == null) return true;

		Scanner messageScanner = new Scanner(message);
		String prefix = messageScanner.next();
		String userNick;
		StringBuilder statusMessage = new StringBuilder();
		StringBuilder userMessage = new StringBuilder();
		int status = 200;

		try {
			switch (prefix) {
				case "CLOSE:" -> {    // Клиент послал сообщение о своем закрытии
					userNick = user.getNickname();
					statusMessage.append(String.format("пользователь @%s вышел из сети", userNick));

					// Закрыть процесс клиента на сервере
					for (ClientProcess client : Server.clientList.values()) {
						client.out.printf("REMOVE: %s\n", user.getNickname());
						client.out.flush();
					}
				}
				case "USER:" -> {    // Сообщение от текущего клиента другому клиенту
					userNick = messageScanner.next();
					statusMessage.append(String.format("сообщение для пользователя @%s", userNick));
					while (messageScanner.hasNext())
						userMessage.append(messageScanner.next()).append(" ");

					ClientProcess client = Server.clientList.get(userNick);
					if (client != null) {
						client.out.printf("USER: %s %s\n", user.getNickname(), userMessage);
						client.out.flush();
					}
				}
				case "USERALL:" -> {    // Сообщение от текущего клиента всем остальным клиентам
					statusMessage.append("сообщение от текущего пользователя всем пользователям");
					while (messageScanner.hasNext())
						userMessage.append(messageScanner.next()).append(" ");

					for (ClientProcess client : Server.clientList.values()) {
						if (client != null && !client.equals(this)) {
							client.out.printf("USERALL: %s %s\n", user.getNickname(), userMessage);
							client.out.flush();
						}
					}
				}
				case "REG:" -> {    // Ответ на регистрацию
					status = messageScanner.nextInt();
					if (status == 200) {
						statusMessage.append("успешная регистрация на сервере");
					} else {
						statusMessage.append("ошибка регистрации клиента на сервере (");
						while (messageScanner.hasNext())
							statusMessage.append(messageScanner.next()).append(" ");
					}
				}
				default -> throw new IllegalArgumentException("Неизвестный префикс входящего сообщения");
			}

			System.out.printf("\n(Клиент \"%s\") - %s > %s\n", user.getNickname(), statusMessage, userMessage);
			if (status != 200) {
				throw new IllegalAccessException(userMessage.toString());
			}

			return true;
		} catch (IllegalAccessException exc) {
			closeClient();
			return false;
		} catch (Exception exc) {
			// TODO - послать сообщение серверу об ошибке в исходном сообщении
			System.out.printf("(Клиент \"%s\") - ошибка разбора входящего сообщения (\"%s\")", user.getNickname(), message);
			return true;
		}
	}

	private void closeClient() {
		try {
			clientSocket.close();
			sendThread.interrupt();
			receiveThread.interrupt();
		} catch (IOException exc) {
			System.out.printf("(Клиент \"%s\") - не получается закрыть клиентский сокет\n", user.getNickname());
			exc.printStackTrace();
		}
	}

	private boolean processIsAlive() {
		return !Thread.currentThread().isInterrupted() && !clientSocket.isClosed();
	}

	public boolean negotiate() {
		try {
			// Получить от клиента запрос регистрации
			String message = in.readLine();
			Scanner messageScanner = new Scanner(message);
			String prefix = messageScanner.next();
			if (!"REG:".equals(prefix)) {
				throw new IOException(String.format("Ожидался запрос регистрации клиента (REG:), получено = \"%s\"", message));
			}
			// Следующий элемент - псевдоним
			String nickname = messageScanner.next();
			if (nickname == null) {
				throw new IOException(String.format("Ожидался псевдоним пользователя, получено = \"%s\"", message));
			}
			// Следующий элемент - пароль
			String password = messageScanner.next();
			if (password == null) {
				throw new IOException(String.format("Ожидался пароль пароля, получено = \"%s\"", message));
			}
			// Проверить на повторную регистрацию
			User current = Server.login(nickname, password);    // Повторная регистрация
			if (current == null) {
				current = Server.register(nickname, password);
				this.user = current;
				assert user != null;
				sendThread.setName("client-" + user.getNickname() + "-send");
				receiveThread.setName("client-" + user.getNickname() + "-receive");
				Server.clientList.put(nickname, this);
			}
			// Отдать всем клиентам полный список пользователей
			for (ClientProcess client : Server.clientList.values()) {
				for (String nickName : Server.userList.keySet()) {
					client.out.printf("LIST: %s\n", nickName);
					client.out.flush();
				}
			}
			// Завершить выдачу текущих пользователей
			out.println("REG: 200");
			out.flush();

			System.out.printf("\nСервер - пользователь @%s вошел в сеть\n", nickname);

			return true;
		} catch (IOException exc) {
			out.println("REG: 500");
			out.flush();
			exc.printStackTrace();
			return false;
		}
	}

	private void composeMessage(String message) {
		Scanner messageScanner = new Scanner(message);
		String prefix = messageScanner.next();
		StringBuilder userMessage = new StringBuilder();
		boolean internal = false;

		try {
			if (Constants.PREFIX_HELP.equals(prefix)) {    //
				internal = true;
			}
//			switch (prefix) {
//			}
		} catch (RuntimeException exc) {
			System.out.println(exc.getMessage());
		}

		if (!internal) {
			out.println(message);
			out.flush();
		}
	}

	public String getNickname() {
		if (user == null) return null;
		return user.getNickname();
	}
}
