package fr.upem.net.tcp.reader;

import java.nio.ByteBuffer;

import java.nio.charset.Charset;

public class StringReader implements Reader {

	private enum State {
		DONE, WAITING_FOR_SIZE, WAITING_FOR_STRING, ERROR
	};

	private final ByteBuffer bb;

	private State state = State.WAITING_FOR_SIZE;

	private String value;

	private IntReader intRead;

	private int size;

	public StringReader(ByteBuffer bb) {
		this.bb = bb;
		intRead = new IntReader(bb);
	}

	@Override
	public ProcessStatus process() {
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}

		switch (state) {
		case WAITING_FOR_SIZE:
			switch (intRead.process()) {
			case DONE:
				size = (int) intRead.get();
				state = State.WAITING_FOR_STRING;
				break;

			case REFILL:
				return ProcessStatus.REFILL;

			default:
				state = State.ERROR;
				return ProcessStatus.ERROR;
			}

		case WAITING_FOR_STRING:
			bb.flip();
			try {
				if (bb.remaining() < size)
					return ProcessStatus.REFILL;

				int limit = bb.limit();
				bb.limit(size);
				value = Charset.forName("UTF8").decode(bb).toString();
				bb.limit(limit);
				intRead.reset();
				state = State.DONE;
				return ProcessStatus.DONE;
			} finally {
				bb.compact();
			}

		default:
			throw new AssertionError();
		}
	}

	@Override
	public Object get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return value;
	}

	@Override
	public void reset() {
		state = State.WAITING_FOR_SIZE;
	}
}