package com.plantmonitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.File;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Porte de scripts/generate-vapid-keys.js — gera data/vapid.json uma vez. */
public class GenerateVapidKeys {

    public static void run() throws Exception {
        File outFile = new File("data/vapid.json");
        if (outFile.exists()) {
            System.out.println("Já existe um par de chaves VAPID em " + outFile.getPath() + ". Apague o arquivo se quiser gerar outro.");
            return;
        }

        Security.addProvider(new BouncyCastleProvider());
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", "BC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();

        String publicKey = base64Url(Utils.encode((ECPublicKey) keyPair.getPublic()));
        String privateKey = base64Url(Utils.encode((ECPrivateKey) keyPair.getPrivate()));

        Map<String, String> out = new LinkedHashMap<>();
        out.put("publicKey", publicKey);
        out.put("privateKey", privateKey);

        outFile.getParentFile().mkdirs();
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(outFile, out);

        System.out.println("Chaves VAPID geradas em " + outFile.getPath());
        System.out.println("Reinicie o backend para carregá-las.");
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
