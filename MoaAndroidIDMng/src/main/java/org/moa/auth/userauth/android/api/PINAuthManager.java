package org.moa.auth.userauth.android.api;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.bouncycastle.util.encoders.Hex;
import org.moa.android.crypto.coreapi.DigestAndroidCoreAPI;
import org.moa.android.crypto.coreapi.SymmetricAndroidCoreAPI;
import org.moa.auth.userauth.client.api.MoaClientMsgPacketLib;

import java.io.UnsupportedEncodingException;
import java.security.KeyStore;

abstract class PINAuthManager implements SharedPreferencesManager {
    final String FORMAT_ENCODE = "UTF-8";
    Context context;
    String uniqueDeviceID;
    KeyStore keyStore;

    void init(Context context, String uniqueDeviceID) {
        this.context = context;
        this.uniqueDeviceID = uniqueDeviceID;
    }

    String generateOrGetRegisterMessage(String id, String password) {
        byte[] idPswRegistMsgGen;
        try {
            final String algorithmName = "SHA256";
            final String hmacAlgorithmName = "HmacSHA256";
            final String transformation = "AES/CBC/PKCS7Padding";
            final byte[] idBytes = id.getBytes(FORMAT_ENCODE);
            final byte[] passwordBytes = password.getBytes(FORMAT_ENCODE);
            final byte[] ivBytes = Hex.decode("00FF0000FF00FF000000FFFF000000FF");
            byte[] keyBytes = new byte[ivBytes.length];
            final byte[] idBytesDigestM = DigestAndroidCoreAPI.hashDigest(algorithmName, idBytes);

            System.arraycopy(idBytesDigestM, 0, keyBytes, 0, ivBytes.length);
            SymmetricAndroidCoreAPI symmetricAndroidCoreAPI = new SymmetricAndroidCoreAPI(transformation, ivBytes, keyBytes);
            final byte[] encPswBytes = symmetricAndroidCoreAPI.symmetricEncryptData(passwordBytes);
            final byte[] pswDigestBytes = DigestAndroidCoreAPI.hashDigest(algorithmName, encPswBytes);
            final byte[] idPswHmacDigestBytes = DigestAndroidCoreAPI.hmacDigest(hmacAlgorithmName, idBytes, pswDigestBytes);
            idPswRegistMsgGen = MoaClientMsgPacketLib.IdPswRegistRequestMsgGen(idBytes.length, idBytes,
                    pswDigestBytes.length, pswDigestBytes, idPswHmacDigestBytes.length, idPswHmacDigestBytes);
        } catch (UnsupportedEncodingException e) {
            Log.d("MoaLib", "[PINAuthManager][generateOrGetRegisterMessage] failed to generate idPswRegistMsgGenProcess message");
            throw new RuntimeException("Failed to generate idPswRegistMsgGenProcess message", e);
        }
        return Base64.encodeToString(idPswRegistMsgGen, Base64.NO_WRAP);
    }

    String generateOrGetLoginRequestMessage(String id, String password, String nonceOTP) {
        byte[] pinLoginRequestMsgGen;
        try {
            final String algorithmName = "SHA256";
            final String hmacAlgorithmName = "HmacSHA256";
            final String transformation = "AES/CBC/PKCS7Padding";
            final byte[] idBytes = id.getBytes(FORMAT_ENCODE);
            final byte[] passwordBytes = password.getBytes(FORMAT_ENCODE);
            final byte[] ivBytes = Hex.decode("00FF0000FF00FF000000FFFF000000FF");
            byte[] keyBytes = new byte[ivBytes.length];
            final byte[] idBytesDigestM = DigestAndroidCoreAPI.hashDigest(algorithmName, idBytes);

            System.arraycopy(idBytesDigestM, 0, keyBytes, 0, ivBytes.length);
            SymmetricAndroidCoreAPI symmetricAndroidCoreAPI = new SymmetricAndroidCoreAPI(transformation, ivBytes, keyBytes);
            final byte[] encPswBytes = symmetricAndroidCoreAPI.symmetricEncryptData(passwordBytes);
            final byte[] pswDigestBytes = DigestAndroidCoreAPI.hashDigest(algorithmName, encPswBytes);
            final byte[] idPswHmacDigestBytes = DigestAndroidCoreAPI.hmacDigest(hmacAlgorithmName, idBytes, pswDigestBytes);
            final byte[] nonceOTPBytes = Hex.decode(nonceOTP);
            pinLoginRequestMsgGen = MoaClientMsgPacketLib.PinLogInRequestMsgGen(idBytes.length, idBytes,
                    pswDigestBytes.length, pswDigestBytes, idPswHmacDigestBytes.length, idPswHmacDigestBytes,
                    nonceOTPBytes.length, nonceOTPBytes);
        } catch (UnsupportedEncodingException e) {
            Log.d("MoaLib", "[PINAuthManager][generateOrGetLoginRequestMessage] failed to generate pinLoginRequestMsgGenProcess message");
            throw new RuntimeException("Failed to generate pinLoginRequestMsgGenProcess message", e);
        }
        return Base64.encodeToString(pinLoginRequestMsgGen, Base64.NO_WRAP);
    }
}
