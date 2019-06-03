package org.moa.auth.userauth.manager;

import android.content.Context;
import android.util.Base64;

import org.moa.android.crypto.coreapi.SymmetricCrypto;

import java.security.KeyStore;

abstract class PINAuth {
    Context context;
    String uniqueDeviceID;
    KeyStore keyStore;
    SymmetricCrypto symmetricCrypto;

    public void init(Context context, String uniqueDeviceID) {
        boolean isContext = (context != null);
        boolean isUniqueDeviceID = (uniqueDeviceID != null && uniqueDeviceID.length() > 0);
        if (isContext && isUniqueDeviceID) {
            this.context = context;
            this.uniqueDeviceID = uniqueDeviceID;
            setSymmetricCryptoInstance();
        }
    }

    private void setSymmetricCryptoInstance() {
        assert symmetricCrypto != null;

        String transformation = "AES/CBC/PKCS7Padding";
        byte[] src = Base64.decode(uniqueDeviceID, Base64.NO_WRAP);
        byte[] key = new byte[32];
        System.arraycopy(src, 0, key, 0, key.length);
        byte[] iv = new byte[16];
        System.arraycopy(src, key.length - 1, iv, 0, iv.length);
        symmetricCrypto = new SymmetricCrypto(transformation, iv, key);
    }
}
