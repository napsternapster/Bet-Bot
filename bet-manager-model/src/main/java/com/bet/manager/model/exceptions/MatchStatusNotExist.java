package com.bet.manager.model.exceptions;

public class MatchStatusNotExist extends RuntimeException {
	
	public MatchStatusNotExist(String message) {
		super(message);
	}

	public MatchStatusNotExist(String message, Throwable cause) {
		super(message, cause);
	}
}
