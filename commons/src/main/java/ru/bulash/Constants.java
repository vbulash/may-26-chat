package ru.bulash;

public class Constants {
	public static final String CHAT_HOST = "127.0.0.1";
	public static final int CHAT_PORT = 1000;
	public static final int AUTH_TIMEOUT = 120;
	// Префиксы сообщений
	public static final String PREFIX_HELP = "HELP:";
	public static final String PREFIX_END = "END:";

	// Команды, полученные клиентом
	public static final String CLIENT_REMOVE = "REMOVE:";	// Сервер сообщил о выходе другого клиента
	public static final String CLIENT_ALL = "ALL:";			// Сообщение с сервера всем клиентам
	public static final String CLIENT_SERVER = "SERVER:";	// Сообщение от сервера данному клиенту
	public static final String CLIENT_USER = "USER:";		// Сообщение от другого клиента данному клиенту
	public static final String CLIENT_USERALL = "USERALL:";	// Сообщение от другого клиента всем пользователям
	public static final String CLIENT_LIST = "LIST:";		// Получение пользователя из списка пользователей на сервере
	public static final String CLIENT_REG = "REG:";			// Ответ от сервера на регистрацию клиента
	// Команды, пересылаемые серверу (через сервер)
	//public static final String SERVER_
}
