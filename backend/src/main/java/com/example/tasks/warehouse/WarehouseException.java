package com.example.tasks.warehouse;

public class WarehouseException extends RuntimeException {

	public WarehouseException(Throwable cause) {
		super(cause.getMessage(), cause);
	}

}
