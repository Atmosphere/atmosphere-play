package org.atmosphere.play;

public interface OutStream {
	void write(String message);

	void close();
}
