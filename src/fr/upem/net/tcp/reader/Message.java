package fr.upem.net.tcp.reader;

import java.nio.ByteBuffer;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public class Message {

	/*
	 * 
	 * Demande de connexion
	 * 
	 */

	public static final int CONNEXION_SANS_MDP = 0;

	public static final int CONNEXION_AVEC_MDP = 1;

	/*
	 * 
	 * R�ponse du serveur
	 * 
	 */

	public static final int LOGIN_ACCEPTED = 2;

	public static final int LOGIN_REFUSED = 3;

	/*
	 * 
	 * L�envoie de message public
	 * 
	 */

	public static final int ENVOIE_MESSAGE_PUBLIC = 4;

	/*
	 * 
	 * La r�ception de message public
	 * 
	 */

	public static final int RECEPTION_MESSAGE_PUBLIC = 5;

	/*
	 * 
	 * Demande de connexion priv�e d�un client
	 * 
	 */

	public static final int DEMANDE_CONNEXION_PRIVEE_DU_CLIENT = 6;

	/*
	 * 
	 * R�ponse du serveur � la connexion priv�e d�un client
	 * 
	 */

	public static final int CONNEXION_PRIVEE_ACCEPTE = 7;

	public static final int CONNEXION_PRIVEE_REFUSE = 8;

	/*
	 * 
	 * Demande de connexion priv�e du serveur
	 * 
	 */

	public static final int DEMANDE_CONNEXION_PRIVEE_DU_SERVEUR = 9;

	/*
	 * 
	 * R�ponse du client
	 * 
	 */

	public static final int ACCEPTE_LA_CONNEXION_PRIVEE_DU_CLIENT = 10;

	public static final int REFUS_DE_CONNEXION_PRIVEE_DU_CLIENT = 11;

	/*
	 * 
	 * Retour du serveur
	 * 
	 */

	public static final int ENVOIE_DES_DONNEES_DE_CONNEXION_DU_SERVEUR = 12;

	/*
	 * 
	 * Envoie du message priv� d�un client
	 * 
	 */

	public static final int CONNEXION_PRIVEE = 13;

	/*
	 * 
	 * Envoie du message priv� d�un client
	 * 
	 */

	public static final int MESSAGE_PRIVE = 14;

	/*
	 * 
	 * R�ponse du serveur
	 * 
	 */

	public static final int ENVOIE_DE_FICHIER = 15;

	private static final Charset UTF8 = Charset.forName("UTF8");

	public final int indice;

	private final int port;

	private final long identication;

	private final String nom;

	private final String passWord;

	private final String message;

	private final String adresse;

	private final String file;

	public Message(int indice, int port, long identication, String nom, String passWord,
				   String message, String adresse, String file) {
		super();
		this.indice = indice;
		this.port = port;
		this.identication = identication;
		this.nom = nom;
		this.passWord = passWord;
		this.message = message;
		this.adresse = adresse;
		this.file = file;

		if (indice < 0 || 16 < indice)
			throw new IllegalArgumentException("indice inconnue " + indice);
	}

	/* 0 9 10 11 */
	public static Message createNon(int indice, String nom) {
		Objects.requireNonNull(nom);

		if (indice != CONNEXION_SANS_MDP && indice != DEMANDE_CONNEXION_PRIVEE_DU_SERVEUR
				&& indice != ACCEPTE_LA_CONNEXION_PRIVEE_DU_CLIENT && indice != REFUS_DE_CONNEXION_PRIVEE_DU_CLIENT)
			throw new IllegalArgumentException("indice inconnue " + indice);
		return new Message(indice, 0, 0, nom, null, null, null, null);
	}

	/* 4 */
	public static Message createMessagePublic(String message) {

		Objects.requireNonNull(message);

		return new Message(ENVOIE_MESSAGE_PUBLIC, 0, 0, null, null, message, null, null);
	}

	/* 14 */
	public static Message createMessagePrive(String message) {

		Objects.requireNonNull(message);

		return new Message(MESSAGE_PRIVE, 0, 0, null, null, message, null, null);
	}

	/* 1 */
	public static Message createNomMdp(String nom, String mdp) {

		Objects.requireNonNull(nom);
		Objects.requireNonNull(mdp);

		return new Message(CONNEXION_AVEC_MDP, 0, 0, nom, mdp, null, null, null);
	}

	/* 5 */
	public static Message createNomMessage(String nom, String message) {

		Objects.requireNonNull(nom);
		Objects.requireNonNull(message);

		return new Message(RECEPTION_MESSAGE_PUBLIC, 0, 0, nom, null, message, null, null);
	}

	/* 15 */
	public static Message createNomFichier(String nom, String fichier) {

		Objects.requireNonNull(nom);
		Objects.requireNonNull(fichier);

		return new Message(ENVOIE_DE_FICHIER, 0, 0, nom, null, null, null, fichier);
	}

	/* 2 3 */
	public static Message createValidationConnexion(boolean val) {
		if (val)
			return new Message(LOGIN_ACCEPTED, 0, 0, null, null, null, null, null);
		return new Message(LOGIN_REFUSED,  0, 0, null, null, null, null, null);
	}

	/* 6 */
	public static Message createDemandeConnexionClient(String nom, String addr, int port) {
		if (port == 0)
			throw new IllegalArgumentException("port==0");

		Objects.requireNonNull(nom);
		Objects.requireNonNull(addr);

		return new Message(DEMANDE_CONNEXION_PRIVEE_DU_CLIENT, port, 0, nom, null, null, addr, null);
	}

	/* 7 8 */
	public static Message createValidationConnexionPrive(boolean val, String expediteur, long iden) {
		if (iden == 0)
			throw new IllegalArgumentException("identification==0");
		Objects.requireNonNull(expediteur);

		if (val)
			return new Message(CONNEXION_PRIVEE_ACCEPTE, 0, 0, expediteur, null, null, null, null);
		return new Message(CONNEXION_PRIVEE_REFUSE, 0, 0, expediteur, null, null, null, null);
	}

	/* 12 */
	public static Message createEnvoieDesDonneeConnexionServeur(String expediteur, int port, long iden) {

		Objects.requireNonNull(expediteur);
		if (port == 0)
			throw new IllegalArgumentException("port==0");
		if (iden == 0)
			throw new IllegalArgumentException("identification==0");

		return new Message(ENVOIE_DES_DONNEES_DE_CONNEXION_DU_SERVEUR, port, iden, expediteur, null, null, null,
				null);
	}

	/* 13 */
	public static Message createConnexionPrive(long iden) {
		if (iden == 0)
			throw new IllegalArgumentException("identification==0");

		return new Message(CONNEXION_PRIVEE, 0, iden, null, null, null, null, null);
	}

	@Override
	public String toString() {
		StringJoiner sj = new StringJoiner(", ", "[", " ]");
		sj.add("indice: " + indice);

		if (0 == port)
			sj.add("port :" + port);

		if (0 == identication)
			sj.add("identication: " + identication);

		if (null != nom)
			sj.add("nom: ").add(nom);

		if (null != passWord)
			sj.add("passWord: ").add(passWord);

		if (null != message)
			sj.add("message: ").add(message);

		if (null != adresse)
			sj.add("adresse: ").add(adresse);

		if (null != file)
			sj.add("file: ").add(file);
		return "Message " + sj.toString();
	}

	/* 0 1 5 6 7 8 9 10 11 12 15 */
	public String getNom() {
		if (indice != CONNEXION_SANS_MDP && indice != CONNEXION_AVEC_MDP && indice != DEMANDE_CONNEXION_PRIVEE_DU_CLIENT
				&& indice != CONNEXION_PRIVEE_ACCEPTE && indice != CONNEXION_PRIVEE_REFUSE
				&& indice != ENVOIE_DE_FICHIER && indice != RECEPTION_MESSAGE_PUBLIC
				&& indice != DEMANDE_CONNEXION_PRIVEE_DU_SERVEUR && indice != ACCEPTE_LA_CONNEXION_PRIVEE_DU_CLIENT
				&& indice != REFUS_DE_CONNEXION_PRIVEE_DU_CLIENT
				&& indice != ENVOIE_DES_DONNEES_DE_CONNEXION_DU_SERVEUR)
			throw new IllegalArgumentException("indice incorrecte " + indice);

		return nom;
	}

	/* 1 */
	public String getPassWord() {

		if (indice != CONNEXION_AVEC_MDP)
			throw new IllegalArgumentException("indice incorrecte " + indice);

		return passWord;
	}

	/* 4 5 14 */
	public String getMessage() {
		if (indice != ENVOIE_MESSAGE_PUBLIC && indice != RECEPTION_MESSAGE_PUBLIC && indice != MESSAGE_PRIVE)
			throw new IllegalArgumentException("indice incorrecte " + indice);

		return message;
	}

	/* 6 12 */
	public String getAdress() {
		if (indice != DEMANDE_CONNEXION_PRIVEE_DU_CLIENT && indice != ENVOIE_DES_DONNEES_DE_CONNEXION_DU_SERVEUR)
			throw new IllegalArgumentException("indice incorrecte " + indice);

		return adresse;
	}

	/* 6 12 */
	public int getPort() {
		if (indice != DEMANDE_CONNEXION_PRIVEE_DU_CLIENT && indice != ENVOIE_DES_DONNEES_DE_CONNEXION_DU_SERVEUR)
			throw new IllegalArgumentException("indice incorrecte " + indice);

		return port;
	}

	/* 7 8 12 13 */
	public long getIdenticationCode() {
		if (indice != CONNEXION_PRIVEE_ACCEPTE && indice != CONNEXION_PRIVEE_REFUSE
				&& indice != ENVOIE_DES_DONNEES_DE_CONNEXION_DU_SERVEUR && indice != CONNEXION_PRIVEE)
			throw new IllegalArgumentException("indice incorrecte " + indice);

		return identication;
	}

	/* 15 */
	public String getFile() {
		if (indice != ENVOIE_DE_FICHIER)
			throw new IllegalArgumentException("indice inconnue " + indice);

		return file;
	}

	private ByteBuffer conversion(String truc) {
		ByteBuffer tmp = UTF8.encode(truc);
		return ByteBuffer.allocate(Integer.BYTES + tmp.remaining()).putInt(tmp.remaining()).put(tmp);
	}

	public ByteBuffer getBuffer() {

		List<ByteBuffer> list = new ArrayList<>();
		list.add(ByteBuffer.allocate(Integer.BYTES).putInt(indice));

		if (0 == port)
			list.add(ByteBuffer.allocate(Integer.BYTES).putInt(port));

		if (0 == identication)
			list.add(ByteBuffer.allocate(Integer.BYTES).putLong(identication));

		if (null != nom)
			list.add(conversion(nom));

		if (null != passWord)
			list.add(conversion(passWord));

		if (null != message)
			list.add(conversion(message));

		if (null != adresse)
			list.add(conversion(adresse));

		if (null != file)
			list.add(conversion(file));

		ByteBuffer ret = ByteBuffer.allocate(list.stream().mapToInt(x -> x.remaining()).sum());
		for (ByteBuffer bb : list)
			ret.put(bb);
		return ret;
	}
	/*
	 * id nom |mdp |mes |ip port cod
	 */
}
