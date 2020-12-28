package fr.upem.net.tcp.reader;

public interface Reader {

	public static enum ProcessStatus {

		DONE, REFILL, ERROR

	};

	public ProcessStatus process();

	public Object get();

	public void reset();

}
