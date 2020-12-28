package fr.upem.net.tcp.reader;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MessageReader implements Reader {

	private enum State {

		DONE, WAITING_FOR_INDEX, WAITING_FOR_MESSAGE, ERROR

	};

	private static final HashMap<Integer, Function<MessageReader, ProcessStatus>> map = new HashMap<>();

	static {
		/* 0 */
		map.put(Message.CONNEXION_SANS_MDP,
				mr -> uniqueString(mr, s -> Message.createNon(Message.CONNEXION_SANS_MDP, s)));

		/* 9 */
		map.put(Message.DEMANDE_CONNEXION_PRIVEE_DU_SERVEUR,
				mr -> uniqueString(mr, s -> Message.createNon(Message.DEMANDE_CONNEXION_PRIVEE_DU_SERVEUR, s)));

		/* 10 */
		map.put(Message.ACCEPTE_LA_CONNEXION_PRIVEE_DU_CLIENT,
				mr -> uniqueString(mr, s -> Message.createNon(Message.ACCEPTE_LA_CONNEXION_PRIVEE_DU_CLIENT, s)));

		/* 11 */
		map.put(Message.REFUS_DE_CONNEXION_PRIVEE_DU_CLIENT,
				mr -> uniqueString(mr, s -> Message.createNon(Message.REFUS_DE_CONNEXION_PRIVEE_DU_CLIENT, s)));

		/* 4 */
		map.put(Message.ENVOIE_MESSAGE_PUBLIC, mr -> uniqueString(mr, s -> Message.createMessagePublic(s)));

		/* 14 */
		map.put(Message.MESSAGE_PRIVE, mr -> uniqueString(mr, s -> Message.createMessagePrive(s)));

		/* 1 */
		map.put(Message.CONNEXION_AVEC_MDP, mr -> doubleString(mr, (s1, s2) -> Message.createNomMdp(s1, s2)));

		/* 5 */
		map.put(Message.RECEPTION_MESSAGE_PUBLIC, mr -> doubleString(mr, (s1, s2) -> Message.createNomMessage(s1, s2)));

		/* 15 */
		map.put(Message.ENVOIE_DE_FICHIER, mr -> doubleString(mr, (s1, s2) -> Message.createNomFichier(s1, s2)));

		/* 2 */
		map.put(Message.LOGIN_ACCEPTED, mr -> {
			mr.value = Message.createValidationConnexion(true);
			mr.state = State.DONE;
			return ProcessStatus.DONE;
		});

		
		/* 3 */
		map.put(Message.LOGIN_REFUSED, mr -> {
			mr.value = Message.createValidationConnexion(false);
			mr.state = State.DONE;
			return ProcessStatus.DONE;
		});

		/* 6 */
		map.put(Message.DEMANDE_CONNEXION_PRIVEE_DU_CLIENT,
				mr -> doubleStringInt(mr, (s1, s2, l) -> Message.createDemandeConnexionClient(s1, s2, l)));

		/* 7 */
		map.put(Message.CONNEXION_PRIVEE_ACCEPTE,
				mr -> uniqueStringLong(mr, (s, l) -> Message.createValidationConnexionPrive(true, s, l)));

		/* 8 */
		map.put(Message.CONNEXION_PRIVEE_REFUSE,
				mr -> uniqueStringLong(mr, (s, l) -> Message.createValidationConnexionPrive(false, s, l)));

		/* 12 */
		map.put(Message.ENVOIE_DES_DONNEES_DE_CONNEXION_DU_SERVEUR,
				mr -> uniqueStringIntLong(mr, (s, i, l) -> Message.createEnvoieDesDonneeConnexionServeur(s, i, l)));

		/* 13 */
		map.put(Message.CONNEXION_PRIVEE, mr -> uniqueLong(mr, (l) -> Message.createConnexionPrive(l)));
	}

	private static ProcessStatus uniqueString(MessageReader mr, Function<String, Message> fun) {
		switch (mr.sr.process()) {

		case DONE:
			mr.value = fun.apply((String) mr.sr.get());
			mr.sr.reset();
			mr.state = State.DONE;
			return ProcessStatus.DONE;

		case REFILL:
			return ProcessStatus.REFILL;

		default:
			mr.state = State.ERROR;
			return ProcessStatus.ERROR;
		}
	}

	private static ProcessStatus doubleString(MessageReader mr, BiFunction<String, String, Message> fun) {
		if (null != mr.str1)
			return uniqueString(mr, s -> {
				String ret = mr.str1;
				mr.str1 = null;
				return fun.apply(ret, s);
			});

		switch (mr.sr.process()) {

		case DONE:
			mr.str1 = (String) mr.sr.get();
			mr.sr.reset();
			return doubleString(mr, fun);

		case REFILL:
			return ProcessStatus.REFILL;

		default:
			mr.state = State.ERROR;
			return ProcessStatus.ERROR;
		}
	}

	private static ProcessStatus doubleStringInt(MessageReader mr, TriFunction<String, String, Integer, Message> fun) {
		if (null == mr.str1) {
			switch (mr.sr.process()) {

			case DONE:
				mr.str1 = (String) mr.sr.get();
				mr.sr.reset();
				break;

			case REFILL:
				return ProcessStatus.REFILL;

			default:
				mr.state = State.ERROR;
				return ProcessStatus.ERROR;
			}
		}
		if (null == mr.str2) {
			switch (mr.sr.process()) {

			case DONE:
				mr.str2 = (String) mr.sr.get();
				mr.sr.reset();
				break;

			case REFILL:
				return ProcessStatus.REFILL;

			default:
				mr.state = State.ERROR;
				return ProcessStatus.ERROR;
			}
		}

		switch (mr.ir.process()) {

		case DONE:
			mr.value = fun.apply(mr.str1, mr.str2, (int) mr.ir.get());
			mr.ir.reset();
			mr.str1 = null;
			mr.str2 = null;
			mr.state = State.DONE;
			return ProcessStatus.DONE;

		case REFILL:
			return ProcessStatus.REFILL;

		default:
			mr.state = State.ERROR;
			return ProcessStatus.ERROR;
		}
	}

	
	private static ProcessStatus uniqueStringLong(MessageReader mr, BiFunction<String, Long, Message> fun) {
		if (null != mr.str1)
			return uniqueLong(mr, s -> {
				String ret = mr.str1;
				mr.str1 = null;
				return fun.apply(ret, s);
			});

		switch (mr.sr.process()) {

		case DONE:
			mr.str1 = (String) mr.sr.get();
			mr.sr.reset();
			return uniqueStringLong(mr, fun);

		case REFILL:
			return ProcessStatus.REFILL;

		default:
			mr.state = State.ERROR;
			return ProcessStatus.ERROR;
		}
	}

	private static ProcessStatus uniqueStringIntLong(MessageReader mr,
			TriFunction<String, Integer, Long, Message> fun) {
		if (null != mr.str1 && mr.recupererInt)
			return uniqueLong(mr, s -> {
				String ret = mr.str1;
				mr.recupererInt = true;
				mr.str1 = null;
				return fun.apply(ret, mr.port, s);
			});

		if (null == mr.str1) {
			switch (mr.sr.process()) {

			case DONE:
				mr.str1 = (String) mr.sr.get();
				mr.sr.reset();
				break;

			case REFILL:
				return ProcessStatus.REFILL;

			default:
				mr.state = State.ERROR;
				return ProcessStatus.ERROR;
			}
		}
		switch (mr.ir.process()) {

		case DONE:
			mr.port = (int) mr.ir.get();
			mr.ir.reset();
			mr.recupererInt = false;
			return uniqueStringIntLong(mr, fun);

		case REFILL:
			return ProcessStatus.REFILL;

		default:
			mr.state = State.ERROR;
			return ProcessStatus.ERROR;
		}

	}

	private static ProcessStatus uniqueLong(MessageReader mr, Function<Long, Message> fun) {
		switch (mr.lr.process()) {

		case DONE:
			mr.value = fun.apply((long) mr.lr.get());
			mr.lr.reset();
			mr.state = State.DONE;
			return ProcessStatus.DONE;

		case REFILL:
			return ProcessStatus.REFILL;

		default:
			mr.state = State.ERROR;
			return ProcessStatus.ERROR;
		}
	}

	private State state = State.WAITING_FOR_INDEX;

	private StringReader sr;

	private IntReader ir;

	private LongReader lr;

	private Message value;

	private int indice;

	private String str1;

	private String str2;

	private int port;

	private boolean recupererInt = true;

	public MessageReader(ByteBuffer bb) {
		bb.clear();
		sr = new StringReader(bb);
		ir = new IntReader(bb);
		;
		lr = new LongReader(bb);
	}

	@Override
	public ProcessStatus process() {

		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}

		switch (state) {

		case WAITING_FOR_INDEX:
			switch (ir.process()) {

			case REFILL:
				return ProcessStatus.REFILL;

			case DONE:
				indice = (int) ir.get();
				ir.reset();
				state = State.WAITING_FOR_MESSAGE;

			default:
				state = State.ERROR;
				return ProcessStatus.ERROR;
			}

		case WAITING_FOR_MESSAGE:
			return map.get(indice).apply(this);

		case ERROR:
		default:
			state = State.ERROR;
			return ProcessStatus.ERROR;
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
		state = State.WAITING_FOR_INDEX;
	}

}