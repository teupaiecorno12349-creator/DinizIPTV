package com.mgplay.app;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class Vault {
    private static final String ALIAS = "mgplay_playlist_v1";
    private Vault() {}

    static String encrypt(String plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] all = new byte[1 + iv.length + encrypted.length];
        all[0] = (byte) iv.length;
        System.arraycopy(iv, 0, all, 1, iv.length);
        System.arraycopy(encrypted, 0, all, 1 + iv.length, encrypted.length);
        return Base64.encodeToString(all, Base64.NO_WRAP);
    }

    static String decrypt(String encoded) throws Exception {
        byte[] all = Base64.decode(encoded, Base64.DEFAULT);
        int ivLength = all[0] & 0xff;
        if (ivLength < 12 || all.length <= ivLength + 1) throw new IllegalArgumentException("Dados criptografados inválidos");
        byte[] iv = Arrays.copyOfRange(all, 1, 1 + ivLength);
        byte[] body = Arrays.copyOfRange(all, 1 + ivLength, all.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }
}