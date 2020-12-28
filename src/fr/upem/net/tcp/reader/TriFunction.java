package fr.upem.net.tcp.reader;

interface TriFunction<A, B, C, R> {
	public R apply(A a, B b, C c);
}
