package nz.mega.sdk;

import cl.fernando.nubemusica.*;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Session token only. The account password is never written to disk or preferences. */
final class MegaSessionStore {
    private static final String ALIAS="nube-mega-session-v1";
    private final AtomicFile file;
    MegaSessionStore(Context context){file=new AtomicFile(new File(context.getNoBackupFilesDir(),"mega-session.enc"));}
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey)store.getKey(ALIAS,null);
    }
    synchronized JSONObject read() throws Exception {
        if(!file.getBaseFile().exists())return null;
        byte[] saved=file.readFully();if(saved.length<29)throw new java.io.IOException("Sesión dañada");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOf(saved,12)));
        byte[] clear=cipher.doFinal(saved,12,saved.length-12);
        try{return new JSONObject(new String(clear,StandardCharsets.UTF_8));}finally{Arrays.fill(clear,(byte)0);}
    }
    synchronized void save(String session,String email,String account) throws Exception {
        byte[] clear=new JSONObject().put("session",session).put("email",email).put("account",account).toString().getBytes(StandardCharsets.UTF_8);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        FileOutputStream output=null;
        try{byte[] encrypted=cipher.doFinal(clear);output=file.startWrite();output.write(cipher.getIV());output.write(encrypted);file.finishWrite(output);}
        catch(Exception e){if(output!=null)file.failWrite(output);throw e;}
        finally{Arrays.fill(clear,(byte)0);}
    }
    synchronized void clear(){file.delete();}
}
