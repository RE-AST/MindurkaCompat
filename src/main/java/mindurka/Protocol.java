package mindurka;

import arc.Core;
import arc.func.Cons;
import arc.func.Func2;
import arc.func.Prov;
import arc.math.Mathf;
import arc.struct.ObjectIntMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Pack;
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
import java.util.Random;
import java.util.zip.CRC32;

public class Protocol {
    private static final byte[] AUTH_HEADER = new byte[] { 43, 76, 12, 45 };

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
            if (Core.settings.getBool("mindurka.enablenet", true)) {
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
        Log.info("[MindurkaCompat] Preparing keys...");

        byte[] pubkeyBytes = Core.settings.getBytes("mindurka.certRSApub");
        byte[] privkeyBytes = Core.settings.getBytes("mindurka.certRSApriv");

        if (pubkeyBytes == null || privkeyBytes == null) {
            Log.info("[MindurkaCompat] Could not find the keys, generating new ones.");
            new Thread(this::generateKeyThread, "Keygen thread").start();
            return;
        }

        KeyFactory factory = Util.yeet(() -> KeyFactory.getInstance(encryptionScheme));

        try {
            pair = new KeyPair(factory.generatePublic(new X509EncodedKeySpec(pubkeyBytes)), factory.generatePrivate(new PKCS8EncodedKeySpec(privkeyBytes)));
            Log.info("[MindurkaCompat] Key loading complete!");
            keyart();
        } catch (InvalidKeySpecException e) {
            Vars.ui.showException("Failed to parse saved key", e);
            Log.err("[MindurkaCompat] Keys are invalid, generating new ones.", e);
            new Thread(this::generateKeyThread, "Keygen thread").start();
        }
    }

    interface ArtFuncConstr {
        ArtFunc generate(CustomRng random, int depth);
    }

    static class ArtGen {
        private ArtGen() {}

        private static ArtFuncConstr[] less2 = new ArtFuncConstr[] {
                ArtInvert::generate, ArtGAvg::generate, ArtAvg::generate,
                ArtMax::generate,
        };
        private static ArtFuncConstr[] less5 = new ArtFuncConstr[] {
                ArtInvert::generate, ArtGAvg::generate, ArtAvg::generate,
                ArtMax::generate,

                ArtConst::generate, ArtHorGrad::generate, ArtVertGrad::generate,
                ArtDotGrad::generate,
        };
        private static ArtFuncConstr[] less8 = new ArtFuncConstr[] {
                ArtConst::generate, ArtHorGrad::generate, ArtVertGrad::generate,
                ArtDotGrad::generate,
        };
        static ArtFunc generate(CustomRng random, int depth) {
            if (depth < 2) return less2[random.nextInt(less2.length)].generate(random, depth);
            if (depth < 5) return less5[random.nextInt(less5.length)].generate(random, depth);
            if (depth < 7) return less8[random.nextInt(less8.length)].generate(random, depth);
            return ArtConst.generate(random, depth);
        }
    }

    abstract static class ArtFunc {
        abstract float sample(float x, float y);
        static String floatStr(float x) { return Float.toString(Mathf.round(x * 100) / 100f); }
    }
    static class ArtConst extends ArtFunc {
        private final float val;
        ArtConst(float val) { this.val = val; }
        @Override float sample(float x, float y) { return val; }
        static ArtFunc generate(CustomRng random, int depth) {
            return new ArtConst(random.nextFloat());
        }
        @Override
        public String toString() { return floatStr(val); }
    }
    static class ArtDotGrad extends ArtFunc {
        private final ArtFunc fn1;
        private final ArtFunc fn2;
        ArtDotGrad(ArtFunc fn1, ArtFunc fn2) {
            this.fn1 = fn1;
            this.fn2 = fn2;
        }
        @Override float sample(float x, float y) {
            return Mathf.pow(Mathf.dst2(fn1.sample(x, y), fn2.sample(x, y), x, y), 1/4f);
        }
        static ArtFunc generate(CustomRng random, int depth) {
            return new ArtDotGrad(ArtGen.generate(random, depth + 1), ArtGen.generate(random, depth + 1));
        }
        @Override
        public String toString() { return "dotgrad("+fn1+", "+fn2+")"; }
    }
    static class ArtHorGrad extends ArtFunc {
        @Override float sample(float x, float y) {
            return x;
        }
        static ArtFunc generate(CustomRng random, int depth) {
            return new ArtHorGrad();
        }
        @Override
        public String toString() { return "hgrad()"; }
    }
    static class ArtVertGrad extends ArtFunc {
        @Override float sample(float x, float y) {
            return y;
        }
        static ArtFunc generate(CustomRng random, int depth) {
            return new ArtVertGrad();
        }
        @Override
        public String toString() { return "vgrad()"; }
    }
    static class ArtAvg extends ArtFunc {
        private final ArtFunc fn1;
        private final ArtFunc fn2;
        public ArtAvg(ArtFunc fn1, ArtFunc fn2) {
            this.fn1 = fn1;
            this.fn2 = fn2;
        }
        @Override float sample(float x, float y) {
            return (fn1.sample(x, y) + fn2.sample(x, y)) / 2;
        }
        static ArtFunc generate(CustomRng random, int depth) {
            return new ArtAvg(ArtGen.generate(random, depth + 1), ArtGen.generate(random, depth + 1));
        }
        @Override
        public String toString() { return "avg("+fn1+", "+fn2+")"; }
    }
    static class ArtGAvg extends ArtFunc {
        private final ArtFunc fn1;
        private final ArtFunc fn2;
        public ArtGAvg(ArtFunc fn1, ArtFunc fn2) {
            this.fn1 = fn1;
            this.fn2 = fn2;
        }
        @Override float sample(float x, float y) {
            return Mathf.sqrt(Mathf.pow(fn1.sample(x, y), 2) * Mathf.pow(fn2.sample(x, y), 2));
        }
        static ArtFunc generate(CustomRng random, int depth) {
            return new ArtGAvg(ArtGen.generate(random, depth + 1), ArtGen.generate(random, depth + 1));
        }
        @Override
        public String toString() { return "gavg("+fn1+", "+fn2+")"; }
    }
    static class ArtInvert extends ArtFunc {
        private final ArtFunc fn;
        public ArtInvert(ArtFunc fn) {
            this.fn = fn;
        }
        @Override float sample(float x, float y) {
            return 1 - fn.sample(x, y);
        }
        static ArtFunc generate(CustomRng random, int depth) {
            return new ArtInvert(ArtGen.generate(random, depth + 1));
        }
        @Override
        public String toString() { return "not("+fn+")"; }
    }
    static class ArtMax extends ArtFunc {
        private final ArtFunc fn1;
        private final ArtFunc fn2;
        public ArtMax(ArtFunc fn1, ArtFunc fn2) {
            this.fn1 = fn1;
            this.fn2 = fn2;
        }
        @Override float sample(float x, float y) {
            return Float.max(fn1.sample(x, y), fn2.sample(x, y));
        }
        static ArtFunc generate(CustomRng random, int depth) {
            return new ArtMax(ArtGen.generate(random, depth + 1), ArtGen.generate(random, depth + 1));
        }
        @Override
        public String toString() { return "max("+fn1+", "+fn2+")"; }
    }
    static class CustomRng {
        private long seed;
        public CustomRng(long seed) {
            this.seed = seed;
        }

        private void cycle() {
            seed += 2359034834752347L;
            seed *= 47;

            int left = Pack.leftInt(seed);
            int right = Pack.rightInt(seed);

            int newLeft = ((left + 28234753) * 173) ^ right;
            int newRight = ((right + 6234174) * 353) ^ left;

            seed = Pack.longInt(newRight, newLeft);
        }

        public float nextFloat() {
            cycle();
            return Math.abs((float) (int) seed / Integer.MAX_VALUE);
        }
        public int nextInt() {
            cycle();
            return (int) seed;
        }
        public int nextInt(int bound) {
            cycle();
            int abs = (int) seed;
            if (abs < 0) abs = -abs;
            return abs % bound;
        }
    }

    private void keyart() {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keyPair().getPrivate());
            byte[] encryptedBody = cipher.doFinal("Nice little art!".getBytes(Vars.charset));
            long seed = 0;
            for (int i = 0; i < encryptedBody.length; i++) {
                seed += 623L * encryptedBody[i] * (i + 1);
            }
            CustomRng random = new CustomRng(seed);

            ArtFunc gen = ArtGen.generate(random, 0);

            Log.info("[MindurkaCompat] Generated key art");
            Log.info("[MindurkaCompat] +----------------------------------+");
            for (int y = 0; y < 16; y++) {
                StringBuilder b = new StringBuilder(32);
                for (int x = 0; x < 32; x++) {
                    float value = gen.sample(x / 31f, y / 15f);
                    if (value < 0.2f) b.append(" ");
                    else if (value < 0.4f) b.append("-");
                    else if (value < 0.6f) b.append("+");
                    else if (value < 0.8f) b.append("#");
                    else b.append("@");
                }
                Log.info("[MindurkaCompat] | "+b+" |");
            }
            Log.info("[MindurkaCompat] +----------------------------------+");
            Log.info("[MindurkaCompat] Function: "+gen);

        } catch (Exception e) {
            Log.err("[MindurkaCompat] Encryption failed.", e);
        }
    }

    private void generateKeyThread() {
        KeyPairGenerator gen = Util.yeet(() -> KeyPairGenerator.getInstance(encryptionScheme));
        gen.initialize(4096);
        KeyPair pair = gen.generateKeyPair();

        assert pair.getPublic().getFormat().equals("X.509");
        assert pair.getPublic().getAlgorithm().equals("RSA");

        Core.app.post(() -> {
            KeyFactory factory = Util.yeet(() -> KeyFactory.getInstance(encryptionScheme));

            Core.settings.put("mindurka.certRSApub", Util.yeet(() -> factory.getKeySpec(pair.getPublic(), X509EncodedKeySpec.class)).getEncoded());
            Core.settings.put("mindurka.certRSApriv", Util.yeet(() -> factory.getKeySpec(pair.getPrivate(), PKCS8EncodedKeySpec.class)).getEncoded());

            Log.info("[MindurkaCompat] Keygen completed successfully!");

            this.pair = pair;
            keyart();
        });
    }

    public KeyPair keyPair() {
        if (pair == null) throw new IllegalStateException("Keys are still generating!");
        return pair;
    }
}
