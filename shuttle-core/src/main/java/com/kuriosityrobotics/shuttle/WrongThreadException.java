package com.kuriosityrobotics.shuttle;

public class WrongThreadException extends RuntimeException {
	public WrongThreadException() {
		super();
	}

	public WrongThreadException(String message) {
		super(message);
	}
}
