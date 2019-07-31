package org.moa.auth.userauth.manager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.KeyPairGeneratorSpec;
import android.util.Base64;
import android.util.Log;

import org.moa.android.crypto.coreapi.PBKDF2;
import org.moa.android.crypto.coreapi.Symmetric;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.StringTokenizer;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.security.auth.x500.X500Principal;

public class AutoLogin extends PINAuth {
    private static final int NONE = 0xA0;
    private static final int AUTO_LOGIN = 0xA1;
    private final String keyAlias = "MoaAutoInfo";
    private PBKDF2 pbkdf2;

    private AutoLogin() {
        initKeyStore();
        pbkdf2 = new PBKDF2("SHA384");
    }

    public static AutoLogin getInstance() {
        return Singleton.instance;
    }

    @Override
    public void init(
            Context context,
            String uniqueDeviceID
    ) {
        super.init(context, uniqueDeviceID);
        try {
            if (!keyStore.containsAlias(keyAlias)) {
                generateKey();
            }
        } catch (KeyStoreException e) {
            Log.d("MoaLib", e.getMessage());
        }
    }

    public String get() {
        StringTokenizer stringTokenizer = new StringTokenizer(
                getValuesInPreferences("Auto.Info"),
                "$"
        );
        String type = stringTokenizer.nextToken();
        String info = stringTokenizer.nextToken();
        if (Integer.parseInt(type) == AUTO_LOGIN) {
            return info;
        } else {
            return "";
        }
    }

    public void set(String password) {
        int autoLoginType = AUTO_LOGIN;
        if (password == null) {
            // Hashing "MoaPlanet" (SHA-512)
            password = "42009FFDDE80CA527DE3E1AB330481F7A4D76C35A3E7F9571BBA626927A25720" +
                    "B13E2C3F4EDE02DB5BA7B71151F8C7FFA5E4D559B7E7FED75DCCF636276B962B";
            autoLoginType = NONE;
        }
        setValuesInPreferences("Auto.Info", autoLoginType + "$" + password);
    }

    private void initKeyStore() {
        try {
            super.keyStore = KeyStore.getInstance("AndroidKeyStore");
            super.keyStore.load(null);
        } catch (KeyStoreException |
                IOException |
                NoSuchAlgorithmException |
                CertificateException e
        ) {
            Log.d("MoaLib", e.getMessage());
        }
    }

    private void generateKey() {
        Calendar startData = Calendar.getInstance();
        Calendar endData = Calendar.getInstance();
        endData.add(Calendar.YEAR, 25);
        try {
            KeyPairGenerator keyPairGenerator =
                    KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
            keyPairGenerator.initialize(
                    new KeyPairGeneratorSpec.Builder(context)
                            .setAlias(keyAlias)
                            .setSerialNumber(BigInteger.ONE)
                            .setSubject(new X500Principal("CN=" + keyAlias))
                            .setStartDate(startData.getTime())
                            .setEndDate(endData.getTime())
                            .build()
            );
            keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException |
                NoSuchProviderException |
                InvalidAlgorithmParameterException e
        ) {
            Log.d("MoaLib", e.getMessage());
        }
    }

    private void setValuesInPreferences(
            String key,
            String value
    ) {
        String encryptValue = "";
        if (key.equals("Auto.Info")) {
            encryptValue = getEncryptContent(value);
        } else if (key.equals("Salt.Info")) {
            byte[] encryptSalt = symmetric.getSymmetricData(
                    Cipher.ENCRYPT_MODE,
                    Base64.decode(value, Base64.NO_WRAP)
            );
            encryptValue = Base64.encodeToString(encryptSalt, Base64.NO_WRAP);
        }
        SharedPreferences pref =
                context.getSharedPreferences("androidIDManager", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(key, encryptValue);
        editor.apply();
    }

    private String getValuesInPreferences(String key) {
        SharedPreferences pref =
                context.getSharedPreferences("androidIDManager", Context.MODE_PRIVATE);
        String value = pref.getString(key, "");
        if (value == null || value.length() == 0) {
            Log.d("MoaLib", "value not validate");
            return "";
        }
        if (key.equals("Auto.Info")) {
            return getDecryptContent(value);
        } else if (key.equals("Salt.Info")) {
            byte[] decrypt = symmetric.getSymmetricData(
                    Cipher.DECRYPT_MODE,
                    Base64.decode(value, Base64.NO_WRAP)
            );
            return Base64.encodeToString(decrypt, Base64.NO_WRAP);
        }
        return "";
    }

    private byte[] getSalt() {
        String base64Salt = getValuesInPreferences("Salt.Info");
        if (base64Salt == null || base64Salt.length() == 0) {
            byte[] salt = new byte[64];
            new SecureRandom().nextBytes(salt);
            setValuesInPreferences("Salt.Info", Base64.encodeToString(salt, Base64.NO_WRAP));
            return salt;
        } else {
            return Base64.decode(base64Salt, Base64.NO_WRAP);
        }
    }

    private byte[] getPBKDF2Data(
            int encOrDecMode,
            byte[] data
    ) {
        byte[] derivedKey = generateDerivedKey();
        if (derivedKey.length != 48) {
            Log.d("MoaLib", "Derived key length is " + derivedKey.length);
            return new byte[0];
        }
        byte[] key = Arrays.copyOf(derivedKey, 32);
        byte[] iv = Arrays.copyOfRange(derivedKey, 32, derivedKey.length);
        return new Symmetric("AES/CBC/PKCS7Padding", iv, key)
                .getSymmetricData(encOrDecMode, data);
    }

    private byte[] generateDerivedKey() {
        int iterationCount = 8192;
        int keySize = 48;
        byte[] salt = getSalt();
        byte[] pw = Base64.decode(uniqueDeviceID, Base64.NO_WRAP);
        return pbkdf2.kdfGen(pw, salt, iterationCount, keySize);
    }

    private byte[] getRSAData(
            int encOrDecMode,
            byte[] data
    ) {
        try {
            if (!keyStore.containsAlias(keyAlias)) {
                generateKey();
            }
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            if (encOrDecMode == Cipher.ENCRYPT_MODE) {
                PublicKey publicKey = keyStore.getCertificate(keyAlias).getPublicKey();
                if (publicKey == null) {
                    Log.d("MoaLib", "Public key is null");
                    return new byte[0];
                }
                cipher.init(encOrDecMode, publicKey);
            } else {
                PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyAlias, null);
                if (privateKey == null) {
                    Log.d("MoaLib", "Private key is null");
                    return new byte[0];
                }
                cipher.init(encOrDecMode, privateKey);
            }
            return cipher.doFinal(data);
        } catch (KeyStoreException |
                NoSuchAlgorithmException |
                NoSuchPaddingException |
                InvalidKeyException |
                BadPaddingException |
                IllegalBlockSizeException |
                UnrecoverableKeyException e
        ) {
            Log.d("MoaLib", e.getMessage());
        }
        return new byte[0];
    }

    private String getEncryptContent(String content) {
        return Base64.encodeToString(
                getRSAData(
                        Cipher.ENCRYPT_MODE,
                        getPBKDF2Data(Cipher.ENCRYPT_MODE, content.getBytes(StandardCharsets.UTF_8))
                ),
                Base64.NO_WRAP
        );
    }

    private String getDecryptContent(String content) {
        return new String(
                getPBKDF2Data(
                        Cipher.DECRYPT_MODE,
                        getRSAData(Cipher.DECRYPT_MODE, Base64.decode(content, Base64.NO_WRAP))
                ),
                StandardCharsets.UTF_8
        );
    }

    private static class Singleton {
        @SuppressLint("StaticFieldLeak")
        private static final AutoLogin instance = new AutoLogin();
    }
}