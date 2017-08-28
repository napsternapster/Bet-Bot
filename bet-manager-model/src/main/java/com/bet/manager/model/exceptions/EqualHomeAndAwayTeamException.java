package com.bet.manager.model.exceptions;

public class EqualHomeAndAwayTeamException extends RuntimeException {

	public EqualHomeAndAwayTeamException(String message) {
		super(message);
	}

	public EqualHomeAndAwayTeamException(String message, Throwable cause) {
		super(message, cause);
	}
}
