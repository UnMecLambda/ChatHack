package fr.upem.net.tcp.client;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import fr.upem.net.tcp.reader.Message;

import static fr.upem.net.tcp.reader.Message.*;

/**
* MessageReader's fill the buffer with a message
*/
public class MessageWriter {	
	private static final Charset UTF8 = StandardCharsets.UTF_8;
	private final ByteBuffer bb;
	
	/**
   	 * MessageWriter's constructor
   	 * @param bb : the related buffer (bbout)
   	 */
    public MessageWriter(ByteBuffer bb) {
        this.bb = bb; 
    }
	
    /**
   	 * Fills the buffer according the id of the message to send
   	 * @param m : the message to send
   	 */
	public void process(Message m) {   
		byte code = (byte)m.indice;
		byte ports = (byte)m.getPort();
		byte identifications = (byte)m.getIdenticationCode();
		bb.put(code);
		switch(m.indice) {
			case CONNEXION_SANS_MDP:
				case DEMANDE_CONNEXION_PRIVEE_DU_SERVEUR :
					case ACCEPTE_LA_CONNEXION_PRIVEE_DU_CLIENT :
				putString(m.getNom());
				break;
			case CONNEXION_AVEC_MDP:
				putString(m.getNom());
				putString(m.getPassWord());
				break;
			case ENVOIE_MESSAGE_PUBLIC :
			case MESSAGE_PRIVE:
				putString(m.getMessage());
				break;
			case DEMANDE_CONNEXION_PRIVEE_DU_CLIENT:
				putString(m.getNom());
				//version de l'ip
				bb.put(Byte.parseByte(m.getAdress()));
				bb.put(ports);
				break;
			case ENVOIE_DES_DONNEES_DE_CONNEXION_DU_SERVEUR:
				putString(m.getNom());
				//version de l'ip
				bb.put(Byte.parseByte(m.getAdress()));
				bb.put(ports);
				bb.put(identifications);
				break;
			case CONNEXION_PRIVEE:
				bb.put(identifications);
				break;
			case ENVOIE_DE_FICHIER:
				putString(m.getMessage());
				putString(m.getFile());
				break;

        }
    }
	
	/**
   	 * put a string and his size in the buffer
   	 * @param s : the string to put
   	 */
	public void putString(String s) {
		ByteBuffer buff = UTF8.encode(s);
		if(bb.remaining() < Integer.BYTES) {
			return;
		}
		bb.putInt(buff.limit());
		if(bb.remaining() < buff.limit()) {
			return;
		}
		bb.put(buff);
		buff.clear();
	}
}
