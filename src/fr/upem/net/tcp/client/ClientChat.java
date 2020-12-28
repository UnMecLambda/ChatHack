package fr.upem.net.tcp.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.upem.net.tcp.reader.Message;
import fr.upem.net.tcp.reader.MessageReader;
import fr.upem.net.tcp.reader.Reader;

import static fr.upem.net.tcp.reader.Message.*;


//java fr.upem.net.tcp.client.ClientChat localhost 7777
public class ClientChat {
    /**
     * Context represents the computed operations by a socket channel, such as sending and receiving from the server
     */

    private enum State {
        LOGIN_PRIVATE,
        LOGIN_PUBLIC,
        CONNECT_PRIVATE,
        CONNECT_PUBLIC,
        ERROR
    };
    static private class Context {
        private boolean isPrivate = false;
        SocketChannel psc;
        final private ClientChat client;
        final private Queue<Message> pqueue = new LinkedList<>();
        final private Queue<String> requestQueue = new LinkedList<>();
        final private ByteBuffer pbbin = ByteBuffer.allocate(BUFFER_SIZE);
        final private ByteBuffer pbbout = ByteBuffer.allocate(BUFFER_SIZE);
        private boolean closed = false;
        final private SelectionKey key;
        private MessageReader messageReader;
        private MessageWriter messageWriter;

        /**
         * Context's constructor
         * @param key : the socket channel's selection key
         * @param client : the client
         * @param isPrivate : true if this socket is a private connexion's socket, otherwise, false
         */
        private Context(SelectionKey key, ClientChat client, boolean isPrivate) {
            this.key = key;
            this.psc = (SocketChannel) key.channel();
            this.client = client;
            this.isPrivate = isPrivate;
            messageReader = new MessageReader(pbbin);
            messageWriter = new MessageWriter(pbbout);
        }

        /**
         * adds a message into que queue, process the bbout buffer, and finally updates the interestOps
         * @param msg : message to send
         */
        private void queueMessage(Message msg) {
            pqueue.add(msg);
            processOut();
            updateInterestOps();
        }

        /**
         * adds a request into the queue, process the bbout buffer, and finally updates the interestOps
         * @param request : http request to send to send
         */
        private void queueRequest(String request) {
            requestQueue.add(request);
            do {
                request = requestQueue.poll();
                messageWriter.putString(request);
            }
            while(pbbout.remaining() >= Integer.BYTES && requestQueue.size() > 0);
            updateInterestOps();
        }

        /**
         * Process the bbin buffer, reads the content of the request/message and treats it
         * @throws IOException
         */
        private void processIn() throws IOException {
            for(;;){
                Reader.ProcessStatus status = messageReader.process();
                switch (status){
                    case DONE:
                        Message msg = (Message)messageReader.get();
                        switch(msg.indice) {
//                            case 0 :
//                                client.ConnexionDemandReceivedWithoutPswd(msg); break;
//                            case 1 :
//                                client.ConnexionDemandReceivedWithPswd(msg); break;
                            case 2 :
                                client.loginAccepted(msg); break;
                            case 3 :
                                client.loginRefused(msg); break;
//                            case 4 :
//                                client.publicMessageClientToServer(msg); break;
                            case 5 :
                                client.publicMessageServerToClient(msg); break;
//                            case 6 :
//                                client.privateConnexionDemandReceivedByTheClient(msg); break;
                            case 7 :
                                client.privateConnexionDemandAcceptedByTheServer(msg);	break;
                            case 8 :
                                client.privateConnexionDemandRefused(msg); break;
                            case 9 :
                                client.privateConnexionDemandReceivedByTheServer(msg); break;
//                            case 10 :
//                                client.privateConnexionDemandAcceptedByTheClient(msg); break;
//                            case 11 :
//                                client.privateConnexionDemandRefusedByTheClient(msg); break;
                            case 12 :
                                client.sendPrivateConnexionDataByTheServer(msg); break;
                            case 13 :
                                client.InitConnexionForAPrivateMessageByTheClient(msg); break;
                            case 14 :
                                client.PrivateMessageFromAClientToAnotherOne(msg); break;
                            case 15 :
                                client.sendFileFromAClientToAnotherOne(msg); break;

                        }
                        messageReader.reset();
                        return;
                    case REFILL:
                        logger.log(Level.INFO, "Le serveur s'est deconnecte");
                        System.exit(0);
                        return;
                    case ERROR:
                        silentlyClose();
                        return;
                }
            }
        }

        /**
         * Process the bbout buffer by pulling the queue and writes into the bbout buffer
         */
        private void processOut() {
            Message m;
            do {
                m = pqueue.poll();
                messageWriter.process(m);
            }
            while(pbbout.remaining() >= Integer.BYTES && pqueue.size() > 0);
        }

        /**
         * Updates the interest ops according the buffers
         */
        private void updateInterestOps() {
            int newOps = 0;
            if(pbbin.hasRemaining() && !closed) {
                newOps |= SelectionKey.OP_READ;
            }
            if(pbbout.position() > 0 ) {
                newOps |= SelectionKey.OP_WRITE;
            }
            if(newOps != 0) {
                key.interestOps(newOps);
            }
            else {
                silentlyClose();
            }
        }

        /**
         * Close the current socket channel without message
         */
        private void silentlyClose() {
            try {
                psc.close();
            } catch (IOException e) {
                // ignore exception
            }
        }

        /**
         * Reads the messages/http requests sent by the server, treats them, and finally updates the interest ops.
         * @throws IOException
         */
        private void doRead() throws IOException {
            if(psc.read(pbbin) == -1) {
                closed = true;
            }
            processIn();
            updateInterestOps();
        }

        /**
         * Writes the messages/http requests to the server and update the interest ops
         * @throws IOException
         */
        private void doWrite() throws IOException {
            pbbout.flip();
            psc.write(pbbout);
            pbbout.compact();
            updateInterestOps();
        }

        /**
         * Tests if the socket is finally connected to the server
         * If the socket channel is private connection one, sends a message to the serve
         * Finally updates the interest ops
         * @throws IOException
         */
        private void doConnect(SelectionKey key) throws IOException {
            if (!((SocketChannel) key.channel()).finishConnect()){
                return;
            }
            Context c = (Context)key.attachment();
            if(c.isPrivate == true) {
                int id = client.sockets.get(psc);
                Message msg = new Message(ACCEPTE_LA_CONNEXION_PRIVEE_DU_CLIENT,1,1,client.login,"",Integer.toString(id),"","");
                queueMessage(msg);
                client.privateChat++;
            }
            updateInterestOps();
        }
    }


    static private int BUFFER_SIZE = 1_024;
    static private Logger logger = Logger.getLogger(ClientChat.class.getName());
    private SelectionKey key;
    final private SocketChannel sc;
    private boolean logged = false;
    private int ChatReceived = 0;
    private int privateChatReceived = 0;
    private int privateChat = 0;
    private final Selector selector;
    private final SocketAddress serverAddress;
    private String login = "";
    private final ArrayList<String> loginReceived = new ArrayList<>();
    private final ArrayList<String> loginPrivateReceived = new ArrayList<>();
    private final ArrayList<String> loginPrivateIssued = new ArrayList<>();
    private final ArrayList<String> dests = new ArrayList<>();

    private final HashMap<Integer, String> privateChats = new HashMap<>();
    private final HashMap<SocketChannel, Integer> sockets = new HashMap<>();
    private final HashMap<SocketChannel, SelectionKey> privateKeys = new HashMap<>();
    /**
     * ClientChat's constructor
     * @param serverAddress : the address of the server
     * @throws IOException
     */
    public ClientChat(SocketAddress serverAddress) throws IOException {
        this.serverAddress = serverAddress;
        sc = SocketChannel.open();
        selector = Selector.open();
    }
    /**
     * id = 0 : a connexion demand has been received without a  password
     * Displays a message to informs the user and updates the list of received demands
     * @param msg : the received message
     */
    private void ConnexionDemandReceivedWithoutPswd(Message msg) {
        ChatReceived++;
        logger.log(Level.INFO,"Demande de connexion sans mdp de la part de " + msg.getNom() +" /n");
        loginReceived.add(msg.getNom());
    }


    /**
     * id = 1 : a connexion demand has been received with a password
     * Displays a message to informs the user and updates the list of received demands
     * @param msg : the received message
     */
    private void ConnexionDemandReceivedWithPswd(Message msg) {
        ChatReceived++;
        logger.log(Level.INFO,"Demande de connexion avec mdp de la part de " + msg.getNom() +" /n");
        System.out.println("Demande de connexion avec mdp de la part de " + msg.getNom() +" /n");
        loginReceived.add(msg.getNom());
    }

    /**
     * id = 2 : The previous authentification is a sucess
     * Displays a message to informs the user, that user can now to send messages
     * @param msg : the received message
     */
    private void loginAccepted(Message msg) {
        logger.log(Level.INFO,"Vous etes maintenant connecte avec le pseudo : " + login);
       System.out.println("Vous etes maintenant connecte avec le pseudo : " + login);
        logged = true;
    }

    /**
     * id = 3 : The previous authentication is a failure
     * Displays a message to informs the user, that user have to do another authentication
     * @param msg : the received message
     */
    private void loginRefused(Message msg) {
        logger.log(Level.INFO,"Pseudo deja pris, essayez un autre pseudo");
        System.out.println("Pseudo deja pris, essayez un autre pseudo");
    }

    /**
     * id = 4 : The client has sent a public message to the user
     * Displays the public message and the sender
     * @param msg : the received message
     */
    private void publicMessageClientToServer(Message msg) {
        logger.log(Level.INFO,msg.getNom() + " dit : " + msg.getMessage());
        System.out.println(msg.getNom() + " dit : " + msg.getMessage());

    }

    /**
     * id = 5 : The server has sent a public message to the user
     * Displays the public message and the sender
     * @param msg : the received message
     */
    private void publicMessageServerToClient(Message msg) {
        logger.log(Level.INFO,msg.getNom() + " dit : " + msg.getMessage());
        System.out.println(msg.getNom() + " dit : " + msg.getMessage());
    }

    /**
     * id = 6 : a private connexion demand has been received by the client
     * Displays a message to informs the user and updates the list of received demands
     * @param msg : the received message
     */
    private void privateConnexionDemandReceivedByTheClient(Message msg) {
        privateChatReceived++;
        logger.log(Level.INFO,"Demande de connexion privee de la part de "
                + msg.getNom() +", /y " + msg.getNom() + " pour accepter, /n " + msg.getNom() + " pour refuser");
        System.out.println("Demande de connexion privee de la part de "
                + msg.getNom() +", /y " + msg.getNom() + " pour accepter, /n " + msg.getNom() + " pour refuser");
        loginPrivateReceived.add(msg.getNom());
    }

    /**
     * id = 7 : the private connection demand has been accepted
     * Displays a message to informs the user, create a new socket channel to connect to the server and fill the maps
     * @param msg : the received message
     */
    private void privateConnexionDemandAcceptedByTheServer(Message msg) throws IOException {
        logger.log(Level.INFO, msg.getNom() + " a accepte la demande de connexion privee, " + "la connexion possede l'id " + msg.getMessage());
        System.out.println(msg.getNom() + " a accepte la demande de connexion privee, " + "la connexion possede l'id " + msg.getMessage());

        SocketChannel psc = SocketChannel.open();
        psc.configureBlocking(false);
        psc.connect(serverAddress);
        SelectionKey privateKey = psc.register(selector, SelectionKey.OP_CONNECT);
        Context privateContext = new Context(privateKey, this, true);
        privateKey.attach(privateContext);

        /*if(msg.login2 == login) {
            privateChats.put(Integer.parseInt(msg.getMessage()), msg.login2);
            dests.add(msg.login2);
        }
        else {*/
            privateChats.put(Integer.parseInt(msg.getMessage()), msg.getNom());
            dests.add(msg.getNom());
        /*}*/
        sockets.put(psc, Integer.parseInt(msg.getMessage()));
        privateKeys.put(psc, privateKey);
    }

    /**
     * id = 8 : The private connexion demand has been refused
     * Displays a message to informs the user
     * @param msg : the received message
     */
    private void privateConnexionDemandRefused(Message msg) {
        if(msg.getNom().equals(msg.getNom())) {
            logger.log(Level.INFO, "Vous avez refuse la demande de connexion.");
           System.out.println("Vous avez refuse la demande de connexion.");
        } else {
            logger.log(Level.INFO, msg.getNom() + " a refuse votre demande de connexion.");
            System.out.println(msg.getNom() + " a refuse votre demande de connexion.");
        }
        loginPrivateIssued.remove(msg.getNom());
    }

    /**
     * id = 9 : The server send a private connexion at the client from another client
     * Displays a message to informs the user and updates the list of received demands
     * @param msg : the received message
     */
    private void privateConnexionDemandReceivedByTheServer(Message msg) {
        privateChatReceived++;
        logger.log(Level.INFO,"Demande de connexion privee de la part de "
                + msg.getNom() +", /y " + msg.getNom() + " pour accepter, /n " + msg.getNom() + " pour refuser");
        System.out.println("Demande de connexion privee de la part de "
                + msg.getNom() +", /y " + msg.getNom() + " pour accepter, /n " + msg.getNom() + " pour refuser");
        loginPrivateReceived.add(msg.getNom());
    }

    /**
     * id = 10 : the private connection demand has been accepted by the client
     * Displays a message to informs the user, create a new socket channel to connect to the server and fill the maps
     * @param msg : the received message
     */
    private void privateConnexionDemandAcceptedByTheClient(Message msg) throws IOException {
        logger.log(Level.INFO, msg.getNom() + " a accepte la demande de connexion privee, " + "la connexion possede l'id " + msg.getMessage());
        System.out.println( msg.getNom() + " a accepte la demande de connexion privee, " + "la connexion possede l'id " + msg.getMessage());

        SocketChannel psc = SocketChannel.open();
        psc.configureBlocking(false);
        psc.connect(serverAddress);
        SelectionKey privateKey = psc.register(selector, SelectionKey.OP_CONNECT);
        Context privateContext = new Context(privateKey, this, true);
        privateKey.attach(privateContext);

        /*if(msg.login2 == login) {
            privateChats.put(Integer.parseInt(msg.getMessage()), msg.login2);
            dests.add(msg.login2);
        }
        else {*/
        privateChats.put(Integer.parseInt(msg.getMessage()), msg.getNom());
        dests.add(msg.getNom());
        /*}*/
        sockets.put(psc, Integer.parseInt(msg.getMessage()));
        privateKeys.put(psc, privateKey);
    }

    /**
     * id = 11 : The private connexion demand has been refused by the client
     * Displays a message to informs the user
     * @param msg : the received message
     */
    private void privateConnexionDemandRefusedByTheClient(Message msg) {
        if(msg.getNom().equals(msg.getNom())) {
            logger.log(Level.INFO, "Vous avez refuse la demande de connexion.");
            System.out.println( "Vous avez refuse la demande de connexion.");
        } else {
            logger.log(Level.INFO, msg.getNom() + " a refuse votre demande de connexion.");
            System.out.println( msg.getNom() + " a refuse votre demande de connexion.");
        }
        loginPrivateIssued.remove(msg.getNom());
    }

    /**
     * id = 12 : The server send data for the private connexion at the client from another client
     * Displays a message to informs the user
     * @param msg : the received message
     */
    private void sendPrivateConnexionDataByTheServer(Message msg) {
    }

    /**
     * id = 13 : The Client who receive an invite, initiate the connexion
     * Displays a message to informs the user
     * @param msg : the received message
     */
    private void InitConnexionForAPrivateMessageByTheClient(Message msg) {
    }

    /**
     * id = 14 :
     * Displays a message to informs the user
     * @param msg : the received message
     */
    private void PrivateMessageFromAClientToAnotherOne(Message msg) {
    }

    /**
     * id = 15 :
     * Displays a message to informs the user
     * @param msg : the received message
     */
    private void sendFileFromAClientToAnotherOne(Message msg) {
    }

    private void displayPrivateCommands() {
        logger.log(Level.INFO, "Entrez une commande : ");
        if(privateChatReceived > 0) {
            for(String str : loginPrivateReceived) {
                logger.log(Level.INFO,"Rappel -> Vous avez recu une demande de connexion privee de la part de " + str);
                System.out.println("Rappel -> Vous avez recu une demande de connexion privee de la part de " + str);
            }
        }
        for(HashMap.Entry<Integer, String> entry : privateChats.entrySet()) {
            logger.log(Level.INFO,"id : " + entry.getKey());
            System.out.println("id : " + entry.getKey());
            logger.log(Level.INFO,"login " + entry.getValue());
           System.out.println("login " + entry.getValue());
        }
    }

    /**
     * Displays informations about the client's state such private connections demands and current private connection
     */
    private void displayEnterCommande() {
        logger.log(Level.INFO, "Entrez une commande : ");
        System.out.println( "Entrez une commande : ");
        if(privateChatReceived > 0) {
            for(String str : loginPrivateReceived) {
                logger.log(Level.INFO,"Rappel -> Vous avez recu une demande de connexion privee de la part de " + str);
                System.out.println("Rappel -> Vous avez recu une demande de connexion privee de la part de " + str);
            }
        }
        for(HashMap.Entry<Integer, String> entry : privateChats.entrySet()) {
            logger.log(Level.INFO,"id : " + entry.getKey());
            System.out.println("id : " + entry.getKey());
            logger.log(Level.INFO,"login " + entry.getValue());
          System.out.println("login " + entry.getValue());

        }
    }


    /**
     * Launch the client, selects the keys and the command console to treat the user's entries
     * @throws IOException
     */
    public void launch() throws  IOException{
        System.out.println("TEST 1");
        sc.configureBlocking(false);
        sc.connect(serverAddress);
        key = sc.register(selector, SelectionKey.OP_CONNECT);
        Context publicContext = new Context(key, this, false);
        key.attach(publicContext);
        Scanner scanner = new Scanner(System.in);
        System.out.println("TEST 2");
        Thread input = new Thread(() -> {
            Message msg;
            String entree;
            logger.log(Level.INFO, "Entrer un pseudo");
            System.out.println("Entrer un pseudo");
            while(scanner.hasNext()) {
                entree = scanner.nextLine();
                String replaceEntree = entree.replaceAll("\\s", "");
                if(!replaceEntree.equals("")) {
                    if(logged == false) {
                        //Connexion
                        login = entree;

                        if(!login.equals("") && !login.equals(null)
                                && !login.contains(" ") && !login.contains("\t")
                                && !login.contains("\n") && !login.contains("\f")
                                && !login.contains("\r") && login.length() >= 3) {
                            //Login Valide
                            // IDEE: DEMANDE DE CONNEXION SANS MDP? Indice: 0 ?

                            msg = new Message(CONNEXION_SANS_MDP,0  ,0  ,"" , "", "",
                                    "", "");
                            publicContext.queueMessage(msg);
                        } else {
                            logger.log(Level.INFO, "Au moins 3 caracteres sans espacements");
                            logger.log(Level.INFO, "Entrer un autre pseudo");
                            System.out.println("Au moins 3 caracteres sans espacements");
                            System.out.println("Entrer un autre pseudo");
                        }
                    } else {
                    //Chat privee
                        if(entree.charAt(0) == '/') {
                            char rep = entree.charAt(1);
                            replaceEntree = entree.replaceAll("\\s", "");
                            if (replaceEntree.equals("/q")) {
                                //On quitte tous les chats
                                //IDEE: RENVOYER UN MESSAGE POUR DIRE QUE L ONT QUITTE et disconnect users
                                // case 16 :
                                //        	      Dans le serveur      		disconnectUser(msg); break;
                                //A CHANGER
                                msg = new Message(16, 0,1  ,login , "", "",
                                        "", "");
                                publicContext.queueMessage(msg);
                            } else if (replaceEntree.equals("/y")) {
                                logger.log(Level.INFO, "/y pseudo de l'expediteur");
                                System.out.println("/y pseudo de l'expediteur");
                                displayEnterCommande();
                            } else if (replaceEntree.equals("/n")) {
                                logger.log(Level.INFO, "/n pseudo de l'expediteur");
                                System.out.println("/n pseudo de l'expediteur");
                                displayEnterCommande();
//                            }else if(rep == 'd' && entree.charAt(2) == ' ') {
//                                //On donne une direction au serveur pour savoir ou sont enregistrer les fichiers
//                                int espace = entree.indexOf(" ");
//                                String dir = entree.substring(espace + 1, entree.length());
//                                msg = new Message(8, dir, "", "", "","","","",);
//                                publicContext.queueMessage(msg);
                            } else if(rep == 'q' && entree.charAt(2) == ' ') {
                                //On quitte le chat prive associe au pseudo indique
                                int espace = entree.indexOf(" ");
                                String exp = entree.substring(espace + 1, entree.length());
                                if(privateChat == 0) {
                                    logger.log(Level.INFO,"Vous n'etes pas en chat privee");
                                    System.out.println("Vous n'etes pas en chat privee");
                                    displayEnterCommande();
                                } else if(login.equals(exp)) {
                                    logger.log(Level.INFO, "vous ne pouvez pas utiliser votre pseudo");
                                    System.out.println("vous ne pouvez pas utiliser votre pseudo");
                                    displayEnterCommande();
                                } else if(dests.contains(exp)) {
                                    //break private connexion case 17 :
                                    //A CHANGER
                                    //        	   dans le serveur         		breakPrivateConnexion(msg); break;
                                    msg = new Message(17, 0, 1, login, exp,"","","");
                                    publicContext.queueMessage(msg);
                                    logger.log(Level.INFO,"Vous avez quitte le chat privee avec " + exp);
                                    System.out.println("Vous avez quitte le chat privee avec " + exp);
                                    int id = 0;
                                    for(HashMap.Entry<Integer, String> entry : privateChats.entrySet()) {
                                        if(entry.getValue().equals(msg.getNom())) {
                                            id = entry.getKey();
                                            privateChats.remove(id);
                                        }
                                    }
                                    sockets.remove(sc);
                                    privateKeys.remove(sc);
                                    privateChat--;
                                } else if(!dests.contains(exp)) {
                                    logger.log(Level.INFO,"Vous n'etes en chat privee avec " + exp);
                                    System.out.println("Vous n'etes en chat privee avec " + exp);
                                    displayEnterCommande();
                                } 																																																		//ANDO
                            } else if(privateChatReceived > 0 && entree.charAt(2) == ' ') {
                                //on repond a une demande de chat prive  : /y pseudo ou /n pseudo
                                String exp = entree.substring(3, entree.length());
                                if(rep == 'y') {
                                    //Accepte
                                    if(login.equals(exp)) {
                                        logger.log(Level.INFO, "vous ne pouvez pas utiliser votre pseudo");
                                        System.out.println("vous ne pouvez pas utiliser votre pseudo");
                                        displayEnterCommande();
                                    } else if(loginPrivateReceived.contains(exp)) {
                                        //Broadcast connexionID serverChat
                                        msg = new Message(-1, 0, 1, login, "","",exp,"");
                                        publicContext.queueMessage(msg);
                                        privateChatReceived--;
                                        loginPrivateReceived.remove(exp);
                                    } else {
                                        logger.log(Level.INFO, exp + " ne vous a pas demande en chat prive");
                                        System.out.println(exp + " ne vous a pas demande en chat prive");

                                        displayEnterCommande();
                                    }
                                } else if(rep == 'n') {
                                    // LOGIN REFUSED
                                    if(login.equals(exp)) {
                                        logger.log(Level.INFO, "Vous ne pouvez pas utiliser votre pseudo");
                                        System.out.println("Vous ne pouvez pas utiliser votre pseudo");
                                        displayEnterCommande();
                                    } else if(loginPrivateReceived.contains(exp)) {
                                        //private connexion refused client
                                        msg = new Message(REFUS_DE_CONNEXION_PRIVEE_DU_CLIENT, 0, 1, exp,login ,"",exp,"");
                                        publicContext.queueMessage(msg);
                                        privateChatReceived--;
                                    } else {
                                        logger.log(Level.INFO, exp + " ne vous a pas demande en chat prive");
                                        System.out.println(exp + " ne vous a pas demande en chat prive");
                                        displayEnterCommande();
                                    }
                                }
                            } else if(privateChatReceived == 0 && entree.charAt(2) == ' ') {
                                if(rep == 'y' || rep == 'n') {
                                    logger.log(Level.INFO, "Personne ne vous a demande en chat prive");
                                    System.out.println("Personne ne vous a demande en chat prive");
                                    displayEnterCommande();
                                }
                            } else {
                                //On envoie une demande de chat prive : /pseudo fichier.format
                                int espace = entree.indexOf(" ");
                                if(espace != -1) {
                                    String dest = entree.substring(1, espace);
                                    String fichier = entree.substring(espace + 1, entree.length());
                                    if(dests.contains(dest)) {
                                        logger.log(Level.INFO, "Vous envoye une requete a : " + dest);
                                        System.out.println("Vous envoye une requete a : " + dest);
                                        //Ici on envoie une requete get apres avoir initialisé la connexion privee
                                        String request = "GET " + fichier + "\r\n";
                                        //PAS SUR
                                        //Verifier Login
                                        msg = new Message(0, 0, 1, "", "","","","");
                                        SocketChannel psc = null;
                                        int id = 0;
                                        for(HashMap.Entry<Integer, String> entry : privateChats.entrySet()) {
                                            if(entry.getValue().equals(dest)) {
                                                id = entry.getKey();
                                            }
                                        }
                                        for(HashMap.Entry<SocketChannel, Integer> entry : sockets.entrySet()) {
                                            if(entry.getValue() == id) {
                                                psc = entry.getKey();
                                            }
                                        }
                                        Context privateContext = (Context)privateKeys.get(psc).attachment();
                                        privateContext.queueRequest(request);
                                        //Finir

                                    } else if(loginPrivateReceived.contains(dest)) {
                                        logger.log(Level.INFO, dest
                                                + " vous demande deja en chat prive, vous pouvez accepte en faisant /y " + dest);
                                        System.out.println(dest+ " vous demande deja en chat prive, vous pouvez accepte en faisant /y " + dest);
                                        displayEnterCommande();
                                    } else if(dest.equals(login)) {
                                        logger.log(Level.INFO,"Vous ne pouvez pas faire une connexion privee avec vous-meme");
                                        System.out.println("Vous ne pouvez pas faire une connexion privee avec vous-meme");
                                        displayEnterCommande();
                                    } else if(!fichier.equals("") && !fichier.contains(" ") && !fichier.contains("\t")
                                            && !fichier.contains("\n") && !fichier.contains("\f") && !fichier.contains("\r")
                                            && !dest.equals("") && !dest.contains(" ") && !dest.contains("\t")
                                            && !dest.contains("\n") && !dest.contains("\f") && !dest.contains("\r")) {

                                        logger.log(Level.INFO,"Vous avez fait une demande de connexion privee a " + dest);
                                        System.out.println("Vous avez fait une demande de connexion privee a " + dest);
                                        msg = new Message(DEMANDE_CONNEXION_PRIVEE_DU_CLIENT, 0, 1, login, "","",dest,fichier);
                                        publicContext.queueMessage(msg);
                                        loginPrivateIssued.add(dest);
                                        displayEnterCommande();
                                    }
                                } else {
                                    logger.log(Level.INFO, "La commande commence par / donc ne sera pas interpretee comme un message public");
                                    System.out.println("La commande commence par / donc ne sera pas interpretee comme un message public");
                                    displayEnterCommande();
                                }
                            }
                        } else if(entree.charAt(0) == '@') {
                            //On parle a quelqu'un
                            int espace = entree.indexOf(" ");
                            if(espace != -1) {
                                String login2 = entree.substring(1, espace);
                                if(login.equals(login2)) {
                                    logger.log(Level.INFO, "Vous ne pouvez pas parler avec vous-meme");
                                    System.out.println("Vous ne pouvez pas parler avec vous-meme");
                                } else if(!login2.equals("")) {
                                    String talking = entree.substring(espace + 1, entree.length());
                                    if(!talking.equals("")) {
                                        msg = new Message(MESSAGE_PRIVE, 0, 1, login, login2,talking,login2,"");
                                        publicContext.queueMessage(msg);
                                    } else {
                                        logger.log(Level.INFO, "La commande commence par @login mais vous n'avez pas mis de message");
                                        System.out.println("La commande commence par @login mais vous n'avez pas mis de message");

                                    }
                                } else {
                                    logger.log(Level.INFO, "La commande commence par @ mais vous n'avez pas mis de destinataire");
                                   System.out.println("La commande commence par @ mais vous n'avez pas mis de destinataire");
                                }
                            } else {
                                logger.log(Level.INFO, "La commande commence par @ donc ne sera pas interpretee comme un message public");
                                System.out.println("La commande commence par @ donc ne sera pas interpretee comme un message public");
                            }
                            displayEnterCommande();
                        } else {
                            //Chat general
                            msg = new Message(ENVOIE_MESSAGE_PUBLIC, 0, 0, login, "",entree,"","");
                            publicContext.queueMessage(msg);
                        }
                    }
                }
                selector.wakeup(); // bug du selector wakeup
            }
            scanner.close();
        });
        input.start();
        while (!Thread.interrupted()) {
            try {
                selector.select(this::processSelectedKeys);
            } catch (UncheckedIOException excep) {
                throw excep.getCause();
            }
        }

    }

    /**
     * Treats a key
     * @param key : the selected key
     */
    private void processSelectedKeys(SelectionKey key) {
        try {
            if (key.isValid() && key.isConnectable()) {
                ((Context) key.attachment()).doConnect(key);
            }
        } catch(IOException ioe) {
            logger.log(Level.INFO,"Connection closed with client due to IOException",ioe);
            System.out.println("Connection closed with client due to IOException");
            throw new UncheckedIOException(ioe);
        }
        try {
            if (key.isValid() && key.isWritable()) {
                ((Context) key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                ((Context) key.attachment()).doRead();
            }
        } catch (IOException e) {
            logger.log(Level.INFO,"Connection closed with client due to IOException",e);
            System.out.println("Connection closed with client due to IOException");
            silentlyClose(key);
        }
    }
    /**
     * Closes the socket channel without message
     * @param key : the socket channel's key
     */
    private void silentlyClose(SelectionKey key) {
        Channel sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    private static void usage(){
        System.out.println("Usage : ClientChat address port");
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        System.out.println("TEST TEST TEST");
        if (args.length != 2){
            usage();
            return;
        }
        System.out.println("TEST TEST");
        new ClientChat(new InetSocketAddress(args[0],Integer.parseInt(args[1]))).launch();
        System.out.println("TEST 0");
    }



}
