package mindurka;

import arc.Core;
import arc.func.Cons;
import arc.func.Prov;
import arc.struct.ObjectIntMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Reflect;
import arc.util.Timer;
import arc.util.io.Reads;
import arc.util.io.ReusableByteOutStream;
import arc.util.serialization.Base64Coder;
import mindustry.Vars;
import mindustry.core.NetClient;
import mindustry.core.Version;
import mindustry.gen.Call;
import mindustry.gen.ClientBinaryPacketReliableCallPacket;
import mindustry.gen.SetFloorCallPacket;
import mindustry.net.Net;
import mindustry.net.Packet;
import mindustry.net.Packets;

import javax.crypto.Cipher;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;
import java.util.zip.CRC32;

public class Protocol {
    private static final byte[] AUTH_HEADER = new byte[] { 43, 76, 12, 45 };

    private static class BoolBox {
        KeyPair newPair = null;
    }

    final BoolBox syncObject = new BoolBox();
    KeyPair pair = null;

    private static class OClientBinaryPacketReliable extends ClientBinaryPacketReliableCallPacket {
        @Override
        public void read(Reads READ, int LENGTH) {
            super.read(READ, LENGTH);
            BAIS.setBytes(Reflect.get(ClientBinaryPacketReliableCallPacket.class, this, "DATA"));
            type = mindustry.io.TypeIO.readString(Packet.READ);
        }

        @Override
        public int getPriority() {
            if (MVars.protocol.passThroughPackets.containsKey(type)) return Packet.priorityHigh;
            return super.getPriority();
        }

        @Override
        public void handleClient() {
            super.handleClient();

            Cons<byte[]> cons = MVars.protocol.passThroughPackets.get(type);
            if (cons == null) return;
            cons.get(contents);
        }
    }

    private static class OSetFloorCallPacket extends SetFloorCallPacket {
        @Override
        public void handleClient() {
            try {
                super.handleClient();
            } catch (Exception e) {
                Log.warn("Failed to set floor: tile=("+(tile == null ? "null" : (tile.x + ":" + tile.y))+"), floor="+(floor == null ? "null" : floor.name)+", overlay="+(overlay == null ? "null" : overlay.name));
            }
        }
    }

    private static final String encryptionScheme = "RSA";
    private static final ReusableByteOutStream byteStream = new ReusableByteOutStream(1024);
    private static final DataOutputStream dataStream = new DataOutputStream(byteStream);

    ObjectMap<String, Cons<byte[]>> passThroughPackets = new ObjectMap<>();
    String addressTCP;

    Protocol() {
        {
            ObjectIntMap<Class<?>> packetToId = Reflect.get(Net.class, null, "packetToId");
            Seq<Class<?>> packetClasses = Reflect.get(Net.class, null, "packetClasses");
            Seq<Prov<?>> packetProvs = Reflect.get(Net.class, null, "packetProvs");

            int id = packetToId.get(ClientBinaryPacketReliableCallPacket.class, -1);
            packetToId.put(OClientBinaryPacketReliable.class, id);
            packetClasses.add(OClientBinaryPacketReliable.class);
            packetProvs.replace(packetProvs.get(id), OClientBinaryPacketReliable::new);

            id = packetToId.get(SetFloorCallPacket.class, -1);
            packetToId.put(OSetFloorCallPacket.class, id);
            packetClasses.add(OSetFloorCallPacket.class);
            packetProvs.replace(packetProvs.get(id), OSetFloorCallPacket::new);
        }

        Vars.net.handleClient(Packets.Connect.class, packet -> {
            Log.info("Connecting to server: @", packet.addressTCP);
            int idx = packet.addressTCP.indexOf('/');
            addressTCP = idx <= 0 ? packet.addressTCP : packet.addressTCP.substring(idx);

            Vars.player.admin = false;

            Reflect.invoke(NetClient.class, Vars.netClient, "reset", Util.noargs);

            if (!Vars.net.client()) {
                Log.info("Connection cancelled.");
                Vars.netClient.disconnectQuietly();
                return;
            }

            Vars.ui.loadfrag.hide();
            Vars.ui.loadfrag.show("@connecting.data");

            Vars.ui.loadfrag.setButton(() -> {
                Vars.ui.loadfrag.hide();
                Vars.netClient.disconnectQuietly();
            });

            String locale = Core.settings.getString("locale");
            if (locale.equals("default")) {
                locale = Locale.getDefault().toString();
            }

            {
                byteStream.reset();

                KeyPair pair = keyPair();

                KeyFactory factory = Util.yeet(() -> KeyFactory.getInstance(encryptionScheme));
                byte[] publicKey = Util.yeet(() -> factory.getKeySpec(pair.getPublic(), X509EncodedKeySpec.class)).getEncoded();
                Util.yeet(() -> dataStream.writeShort(publicKey.length));
                Util.yeet(() -> dataStream.write(publicKey));

                Util.yeet(dataStream::close);
                Call.serverBinaryPacketReliable("mindurka.connect", byteStream.getBytes());
            }

            String uuid = Vars.platform.getUUID();
            if (uuid == null) {
                Vars.ui.showErrorMessage("@invalidid");
                Vars.ui.loadfrag.hide();
                Vars.netClient.disconnectQuietly();
                return;
            }

            Packets.ConnectPacket c = new Packets.ConnectPacket();
            c.name = Vars.player.name;
            c.locale = locale;
            c.mods = Vars.mods.getModStrings();
            c.mobile = Vars.mobile;
            c.versionType = Version.type;
            c.color = Vars.player.color.rgba();
            c.usid = Reflect.invoke(NetClient.class, Vars.netClient, "getUsid", new Object[] { packet.addressTCP }, String.class);
            c.uuid = uuid;

            if (c.uuid == null) {
                Vars.ui.showErrorMessage("@invalidid");
                Vars.ui.loadfrag.hide();
                Vars.netClient.disconnectQuietly();
                return;
            }

            Vars.net.send(c, true);
        });

        passThroughPackets.put("mindurka.confirmConnect", packet -> {
            try {
                DataInputStream stream = new DataInputStream(new ByteArrayInputStream(packet));

                byte[] nonce = new byte[32];
                stream.readFully(nonce);
                long time = stream.readLong();

                byteStream.reset();
                dataStream.write(AUTH_HEADER);
                dataStream.writeUTF(addressTCP);
                dataStream.write(nonce);
                dataStream.writeLong(time);
                dataStream.close();
                byte[] body = byteStream.toByteArray();

                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, keyPair().getPrivate());
                byte[] encryptedBody = cipher.doFinal(body);

                String locale = Core.settings.getString("locale");
                if (locale.equals("default")) {
                    locale = Locale.getDefault().toString();
                }

                String uuid = Vars.platform.getUUID();
                if (uuid == null) {
                    Vars.ui.showErrorMessage("@invalidid");
                    Vars.ui.loadfrag.hide();
                    Vars.netClient.disconnectQuietly();
                    return;
                }

                byteStream.reset();
                dataStream.writeShort(encryptedBody.length);
                dataStream.write(encryptedBody);
                dataStream.writeInt(MVars.version);
                dataStream.writeUTF(Vars.player.name);
                {
                    Seq<String> strings = Vars.mods.getModStrings();
                    dataStream.writeShort(strings.size);
                    for (String string : strings) dataStream.writeUTF(string);
                }
                dataStream.writeBoolean(Vars.mobile);
                dataStream.writeUTF(Version.type);
                dataStream.writeInt(Vars.player.color.rgba());
                dataStream.writeUTF(Reflect.invoke(NetClient.class, Vars.netClient, "getUsid", new Object[] { addressTCP }, String.class));

                byte[] b = Base64Coder.decode(uuid);
                dataStream.write(b);
                CRC32 crc = new CRC32();
                crc.update(Base64Coder.decode(uuid), 0, b.length);
                dataStream.writeLong(crc.getValue());

                dataStream.writeUTF(locale);
                dataStream.close();

                Call.serverBinaryPacketReliable("mindurka.verifyKey", byteStream.toByteArray());
            } catch (Exception e) {
                Vars.ui.showException("Protocol error", e);
                Vars.ui.loadfrag.hide();
                Vars.netClient.disconnectQuietly();
            }
        });

        prepareKeys();
    }

    private void prepareKeys() {
        byte[] pubkeyBytes = Core.settings.getBytes("mindurka.certRSApub");
        byte[] privkeyBytes = Core.settings.getBytes("mindurka.certRSApriv");

        if (pubkeyBytes == null || privkeyBytes == null) {
            generateKeyAsync();
            return;
        }

        KeyFactory factory = Util.yeet(() -> KeyFactory.getInstance(encryptionScheme));

        try {
            pair = new KeyPair(factory.generatePublic(new X509EncodedKeySpec(pubkeyBytes)), factory.generatePrivate(new PKCS8EncodedKeySpec(privkeyBytes)));
        } catch (InvalidKeySpecException e) {
            Vars.ui.showException("Failed to parse saved key", e);
            generateKeyAsync();
        }
    }

    private void generateKeyAsync() {
        new Thread(this::generateKeyThread, "Keygen thread").start();

        final Timer.Task[] task = new Timer.Task[1];
        task[0] = Timer.schedule(() -> {
            synchronized (syncObject) {
                if (syncObject.newPair == null) return;
                pair = syncObject.newPair;
            }

            assert pair.getPublic().getFormat().equals("X.509");
            assert pair.getPublic().getAlgorithm().equals("RSA");

            KeyFactory factory = Util.yeet(() -> KeyFactory.getInstance(encryptionScheme));

            Core.settings.put("mindurka.certRSApub", Util.yeet(() -> factory.getKeySpec(pair.getPublic(), X509EncodedKeySpec.class)).getEncoded());
            Core.settings.put("mindurka.certRSApriv", Util.yeet(() -> factory.getKeySpec(pair.getPrivate(), PKCS8EncodedKeySpec.class)).getEncoded());

            task[0].cancel();
        }, 0.5f, 0.5f);
    }

    private void generateKeyThread() {
        KeyPairGenerator gen = Util.yeet(() -> KeyPairGenerator.getInstance(encryptionScheme));
        gen.initialize(4096);
        KeyPair pair = gen.generateKeyPair();

        synchronized (syncObject) {
            syncObject.newPair = pair;
        }
    }

    public KeyPair keyPair() {
        if (pair == null) throw new IllegalStateException("Keys are still generating!");
        return pair;
    }
}
