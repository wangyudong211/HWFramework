package com.huawei.security.keystore;

import android.os.IBinder;
import android.security.keystore.KeyStoreCryptoOperation;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.huawei.security.HwKeystoreManager;
import com.huawei.security.keymaster.HwKeyCharacteristics;
import com.huawei.security.keymaster.HwKeymasterArguments;
import com.huawei.security.keymaster.HwKeymasterDefs;
import com.huawei.security.keymaster.HwKeymasterUtils;
import com.huawei.security.keymaster.HwOperationResult;
import com.huawei.security.keystore.ArrayUtils;
import com.huawei.security.keystore.HwKeyProperties;
import com.huawei.security.keystore.HwUniversalKeyStoreCryptoOperationStreamer;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

public class HwUniversalKeyStoreRSACipherSpi extends CipherSpi implements KeyStoreCryptoOperation {
    private HwUniversalKeyStoreCryptoOperationStreamer mAdditionalAuthenticationDataStreamer;
    private boolean mAdditionalAuthenticationDataStreamerClosed;
    private Exception mCachedException;
    private boolean mEncrypting;
    private HwUniversalKeyStoreKey mKey;
    private final HwKeystoreManager mKeyStore = HwKeystoreManager.getInstance();
    private int mKeymasterPadding;
    private int mKeymasterPaddingOverride = -1;
    private int mKeymasterPurposeOverride = -1;
    private HwUniversalKeyStoreCryptoOperationStreamer mMainDataStreamer;
    private int mModulusSizeBytes = -1;
    private long mOperationHandle;
    private IBinder mOperationToken;
    private SecureRandom mRng;

    static class OAEPWithMGF1Padding extends HwUniversalKeyStoreRSACipherSpi {
        private static final String MGF_ALGORITGM_MGF1 = "MGF1";
        private int mDigestOutputSizeBytes;
        private int mKeymasterDigest = -1;

        OAEPWithMGF1Padding(int keymasterDigest) {
            super(2);
            this.mKeymasterDigest = keymasterDigest;
            this.mDigestOutputSizeBytes = (HwKeymasterUtils.getDigestOutputSizeBits(keymasterDigest) + 7) / 8;
        }

        /* access modifiers changed from: protected */
        public final void initAlgorithmSpecificParameters() throws InvalidKeyException {
        }

        /* access modifiers changed from: protected */
        public final void initAlgorithmSpecificParameters(@Nullable AlgorithmParameterSpec params) throws InvalidAlgorithmParameterException {
            if (params != null) {
                if (params instanceof OAEPParameterSpec) {
                    OAEPParameterSpec spec = (OAEPParameterSpec) params;
                    if (MGF_ALGORITGM_MGF1.equalsIgnoreCase(spec.getMGFAlgorithm())) {
                        String jcaDigest = spec.getDigestAlgorithm();
                        try {
                            int keymasterDigest = HwKeyProperties.Digest.toKeymaster(jcaDigest);
                            switch (keymasterDigest) {
                                case 2:
                                case 3:
                                case 4:
                                case 5:
                                case 6:
                                    AlgorithmParameterSpec mgfParams = spec.getMGFParameters();
                                    if (mgfParams == null) {
                                        throw new InvalidAlgorithmParameterException("MGF parameters must be provided");
                                    } else if (mgfParams instanceof MGF1ParameterSpec) {
                                        String mgf1JcaDigest = ((MGF1ParameterSpec) mgfParams).getDigestAlgorithm();
                                        if (HwKeyProperties.DIGEST_SHA1.equalsIgnoreCase(mgf1JcaDigest)) {
                                            PSource pSource = spec.getPSource();
                                            if (pSource instanceof PSource.PSpecified) {
                                                byte[] pSourceValue = ((PSource.PSpecified) pSource).getValue();
                                                if (pSourceValue == null || pSourceValue.length <= 0) {
                                                    this.mKeymasterDigest = keymasterDigest;
                                                    this.mDigestOutputSizeBytes = (HwKeymasterUtils.getDigestOutputSizeBits(keymasterDigest) + 7) / 8;
                                                    return;
                                                }
                                                throw new InvalidAlgorithmParameterException("Unsupported source of encoding input P: " + pSource + ". Only pSpecifiedEmpty (PSource.PSpecified.DEFAULT) supported");
                                            }
                                            throw new InvalidAlgorithmParameterException("Unsupported source of encoding input P: " + pSource + ". Only pSpecifiedEmpty (PSource.PSpecified.DEFAULT) supported");
                                        }
                                        throw new InvalidAlgorithmParameterException("Unsupported MGF1 digest: " + mgf1JcaDigest + ". Only " + HwKeyProperties.DIGEST_SHA1 + " supported");
                                    } else {
                                        throw new InvalidAlgorithmParameterException("Unsupported MGF parameters: " + mgfParams + ". Only MGF1ParameterSpec supported");
                                    }
                                default:
                                    throw new InvalidAlgorithmParameterException("Unsupported digest: " + jcaDigest);
                            }
                        } catch (IllegalArgumentException e) {
                            throw new InvalidAlgorithmParameterException("Unsupported digest: " + jcaDigest, e);
                        }
                    } else {
                        throw new InvalidAlgorithmParameterException("Unsupported MGF: " + spec.getMGFAlgorithm() + ". Only " + MGF_ALGORITGM_MGF1 + " supported");
                    }
                } else {
                    throw new InvalidAlgorithmParameterException("Unsupported parameter spec: " + params + ". Only OAEPParameterSpec supported");
                }
            }
        }

        /* access modifiers changed from: protected */
        public final void initAlgorithmSpecificParameters(@Nullable AlgorithmParameters params) throws InvalidAlgorithmParameterException {
            if (params != null) {
                try {
                    OAEPParameterSpec spec = (OAEPParameterSpec) params.getParameterSpec(OAEPParameterSpec.class);
                    if (spec != null) {
                        initAlgorithmSpecificParameters((AlgorithmParameterSpec) spec);
                        return;
                    }
                    throw new InvalidAlgorithmParameterException("OAEP parameters required, but not provided in parameters: " + params);
                } catch (InvalidParameterSpecException e) {
                    throw new InvalidAlgorithmParameterException("OAEP parameters required, but not found in parameters: " + params, e);
                }
            }
        }

        /* access modifiers changed from: protected */
        public final AlgorithmParameters engineGetParameters() {
            OAEPParameterSpec spec = new OAEPParameterSpec(HwKeyProperties.Digest.fromKeymaster(this.mKeymasterDigest), MGF_ALGORITGM_MGF1, MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
            try {
                AlgorithmParameters params = AlgorithmParameters.getInstance("OAEP");
                params.init(spec);
                return params;
            } catch (NoSuchAlgorithmException e) {
                throw new ProviderException("Failed to obtain OAEP AlgorithmParameters", e);
            } catch (InvalidParameterSpecException e2) {
                throw new ProviderException("Failed to initialize OAEP AlgorithmParameters with an IV", e2);
            }
        }

        /* access modifiers changed from: protected */
        public final void addAlgorithmSpecificParametersToBegin(HwKeymasterArguments keymasterArgs) {
            HwUniversalKeyStoreRSACipherSpi.super.addAlgorithmSpecificParametersToBegin(keymasterArgs);
            keymasterArgs.addEnum(HwKeymasterDefs.KM_TAG_DIGEST, this.mKeymasterDigest);
        }

        /* access modifiers changed from: protected */
        public final void loadAlgorithmSpecificParametersFromBeginResult(@NonNull HwKeymasterArguments keymasterArgs) {
            HwUniversalKeyStoreRSACipherSpi.super.loadAlgorithmSpecificParametersFromBeginResult(keymasterArgs);
        }

        /* access modifiers changed from: protected */
        public final int getAdditionalEntropyAmountForBegin() {
            return 0;
        }

        /* access modifiers changed from: protected */
        public final int getAdditionalEntropyAmountForFinish() {
            if (isEncrypting()) {
                return this.mDigestOutputSizeBytes;
            }
            return 0;
        }
    }

    public static class OAEPWithSHA256AndMGF1Padding extends OAEPWithMGF1Padding {
        public OAEPWithSHA256AndMGF1Padding() {
            super(4);
        }
    }

    public static class OAEPWithSHA384AndMGF1Padding extends OAEPWithMGF1Padding {
        public OAEPWithSHA384AndMGF1Padding() {
            super(5);
        }
    }

    public static class OAEPWithSHA512AndMGF1Padding extends OAEPWithMGF1Padding {
        public OAEPWithSHA512AndMGF1Padding() {
            super(6);
        }
    }

    public static final class PKCS1Padding extends HwUniversalKeyStoreRSACipherSpi {
        public PKCS1Padding() {
            super(4);
        }

        /* access modifiers changed from: protected */
        public boolean adjustConfigForEncryptingWithPrivateKey() {
            setKeymasterPurposeOverride(2);
            setKeymasterPaddingOverride(5);
            return true;
        }

        /* access modifiers changed from: protected */
        public void initAlgorithmSpecificParameters() throws InvalidKeyException {
        }

        /* access modifiers changed from: protected */
        public void initAlgorithmSpecificParameters(@Nullable AlgorithmParameterSpec params) throws InvalidAlgorithmParameterException {
            if (params != null) {
                throw new InvalidAlgorithmParameterException("Unexpected parameters: " + params + ". No parameters supported");
            }
        }

        /* access modifiers changed from: protected */
        public void initAlgorithmSpecificParameters(@Nullable AlgorithmParameters params) throws InvalidAlgorithmParameterException {
            if (params != null) {
                throw new InvalidAlgorithmParameterException("Unexpected parameters: " + params + ". No parameters supported");
            }
        }

        /* access modifiers changed from: protected */
        public AlgorithmParameters engineGetParameters() {
            return null;
        }

        /* access modifiers changed from: protected */
        public final int getAdditionalEntropyAmountForBegin() {
            return 0;
        }

        /* access modifiers changed from: protected */
        public final int getAdditionalEntropyAmountForFinish() {
            if (isEncrypting()) {
                return getModulusSizeBytes();
            }
            return 0;
        }
    }

    HwUniversalKeyStoreRSACipherSpi(int keymasterPadding) {
        this.mKeymasterPadding = keymasterPadding;
    }

    /* access modifiers changed from: protected */
    public final void engineSetMode(String mode) throws NoSuchAlgorithmException {
        throw new UnsupportedOperationException();
    }

    /* access modifiers changed from: protected */
    public final void engineSetPadding(String arg0) throws NoSuchPaddingException {
        throw new UnsupportedOperationException();
    }

    /* access modifiers changed from: protected */
    public final int engineGetBlockSize() {
        return 0;
    }

    /* access modifiers changed from: protected */
    public final int engineGetKeySize(Key key) throws InvalidKeyException {
        throw new UnsupportedOperationException();
    }

    /* access modifiers changed from: protected */
    public final int engineGetOutputSize(int inputLen) {
        return getModulusSizeBytes();
    }

    /* access modifiers changed from: protected */
    public final int getModulusSizeBytes() {
        if (this.mModulusSizeBytes != -1) {
            return this.mModulusSizeBytes;
        }
        throw new IllegalStateException("Not initialized");
    }

    /* access modifiers changed from: protected */
    public final byte[] engineGetIV() {
        return null;
    }

    /* access modifiers changed from: protected */
    public AlgorithmParameters engineGetParameters() {
        return null;
    }

    /* access modifiers changed from: protected */
    public void resetAll() {
        this.mModulusSizeBytes = -1;
        this.mKeymasterPaddingOverride = -1;
        IBinder operationToken = this.mOperationToken;
        if (operationToken != null) {
            this.mKeyStore.abort(operationToken);
        }
        this.mEncrypting = false;
        this.mKeymasterPurposeOverride = -1;
        this.mKey = null;
        this.mRng = null;
        this.mOperationToken = null;
        this.mOperationHandle = 0;
        this.mMainDataStreamer = null;
        this.mAdditionalAuthenticationDataStreamer = null;
        this.mAdditionalAuthenticationDataStreamerClosed = false;
        this.mCachedException = null;
    }

    /* access modifiers changed from: protected */
    @CallSuper
    public void resetWhilePreservingInitState() {
        IBinder operationToken = this.mOperationToken;
        if (operationToken != null) {
            this.mKeyStore.abort(operationToken);
        }
        this.mOperationToken = null;
        this.mOperationHandle = 0;
        this.mMainDataStreamer = null;
        this.mAdditionalAuthenticationDataStreamer = null;
        this.mAdditionalAuthenticationDataStreamerClosed = false;
        this.mCachedException = null;
    }

    /* access modifiers changed from: protected */
    public void addAlgorithmSpecificParametersToBegin(HwKeymasterArguments keymasterArgs) {
        keymasterArgs.addEnum(HwKeymasterDefs.KM_TAG_ALGORITHM, 1);
        int keymasterPadding = getKeymasterPaddingOverride();
        if (keymasterPadding == -1) {
            keymasterPadding = this.mKeymasterPadding;
        }
        keymasterArgs.addEnum(HwKeymasterDefs.KM_TAG_PADDING, keymasterPadding);
        int purposeOverride = getKeymasterPurposeOverride();
        if (purposeOverride == -1) {
            return;
        }
        if (purposeOverride == 2 || purposeOverride == 3) {
            keymasterArgs.addEnum(HwKeymasterDefs.KM_TAG_DIGEST, 0);
        }
    }

    /* access modifiers changed from: protected */
    public int getAdditionalEntropyAmountForBegin() {
        return 0;
    }

    private void ensureKeystoreOperationInitialized() throws InvalidKeyException, InvalidAlgorithmParameterException {
        int purpose;
        if (this.mMainDataStreamer != null || this.mCachedException != null) {
            return;
        }
        if (this.mKey != null) {
            HwKeymasterArguments keymasterInputArgs = new HwKeymasterArguments();
            addAlgorithmSpecificParametersToBegin(keymasterInputArgs);
            byte[] additionalEntropy = HwUniversalKeyStoreCryptoOperationUtils.getRandomBytesToMixIntoKeystoreRng(this.mRng, getAdditionalEntropyAmountForBegin());
            if (this.mKeymasterPurposeOverride != -1) {
                purpose = this.mKeymasterPurposeOverride;
            } else {
                purpose = this.mEncrypting ? 0 : 1;
            }
            HwOperationResult opResult = this.mKeyStore.begin(this.mKey.getAlias(), purpose, true, keymasterInputArgs, additionalEntropy, this.mKey.getUid());
            if (opResult != null) {
                this.mOperationToken = opResult.token;
                this.mOperationHandle = opResult.operationHandle;
                GeneralSecurityException e = HwUniversalKeyStoreCryptoOperationUtils.getExceptionForCipherInit(this.mKeyStore, this.mKey, opResult.resultCode);
                if (e != null) {
                    if (e instanceof InvalidKeyException) {
                        throw ((InvalidKeyException) e);
                    } else if (e instanceof InvalidAlgorithmParameterException) {
                        throw ((InvalidAlgorithmParameterException) e);
                    } else {
                        throw new ProviderException("Unexpected exception type", e);
                    }
                } else if (this.mOperationToken == null) {
                    throw new ProviderException("Keystore returned null operation token");
                } else if (this.mOperationHandle != 0) {
                    loadAlgorithmSpecificParametersFromBeginResult(opResult.outParams);
                    this.mMainDataStreamer = createMainDataStreamer(this.mKeyStore, opResult.token);
                    this.mAdditionalAuthenticationDataStreamer = createAdditionalAuthenticationDataStreamer(this.mKeyStore, opResult.token);
                    this.mAdditionalAuthenticationDataStreamerClosed = false;
                } else {
                    throw new ProviderException("Keystore returned invalid operation handle");
                }
            } else {
                throw new ProviderException("Failed to communicate with keystore service");
            }
        } else {
            throw new IllegalStateException("Not initialized");
        }
    }

    /* access modifiers changed from: protected */
    public void loadAlgorithmSpecificParametersFromBeginResult(@NonNull HwKeymasterArguments keymasterArgs) {
    }

    /* access modifiers changed from: protected */
    @NonNull
    public HwUniversalKeyStoreCryptoOperationStreamer createMainDataStreamer(HwKeystoreManager keyStore, IBinder operationToken) {
        return new HwUniversalKeyStoreCryptoOperationStreamer(new HwUniversalKeyStoreCryptoOperationStreamer.MainDataStream(keyStore, operationToken));
    }

    /* access modifiers changed from: protected */
    @Nullable
    public HwUniversalKeyStoreCryptoOperationStreamer createAdditionalAuthenticationDataStreamer(HwKeystoreManager keyStore, IBinder operationToken) {
        return null;
    }

    /* access modifiers changed from: protected */
    public boolean adjustConfigForEncryptingWithPrivateKey() {
        return false;
    }

    /* access modifiers changed from: protected */
    public final void initKey(int opmode, Key key) throws InvalidKeyException {
        HwUniversalKeyStoreKey keystoreKey;
        if (key == null) {
            throw new InvalidKeyException("Unsupported key: null");
        } else if (HwKeyProperties.KEY_ALGORITHM_RSA.equalsIgnoreCase(key.getAlgorithm())) {
            if (key instanceof HwUniversalKeyStorePrivateKey) {
                keystoreKey = (HwUniversalKeyStoreKey) key;
            } else if (key instanceof HwUniversalKeyStorePublicKey) {
                keystoreKey = (HwUniversalKeyStoreKey) key;
            } else {
                throw new InvalidKeyException("Unsupported key type: " + key);
            }
            if (keystoreKey instanceof PrivateKey) {
                switch (opmode) {
                    case 1:
                    case 3:
                        if (!adjustConfigForEncryptingWithPrivateKey()) {
                            throw new InvalidKeyException("RSA private keys cannot be used with " + opmodeToString(opmode) + " and padding " + HwKeyProperties.EncryptionPadding.fromKeymaster(this.mKeymasterPadding) + ". Only RSA public keys supported for this mode");
                        }
                        break;
                    case 2:
                    case 4:
                        break;
                    default:
                        throw new InvalidKeyException("RSA private keys cannot be used with opmode: " + opmode);
                }
            } else {
                switch (opmode) {
                    case 1:
                    case 3:
                        break;
                    case 2:
                    case 4:
                        throw new InvalidKeyException("RSA public keys cannot be used with " + opmodeToString(opmode) + " and padding " + HwKeyProperties.EncryptionPadding.fromKeymaster(this.mKeymasterPadding) + ". Only RSA private keys supported for this opmode.");
                    default:
                        throw new InvalidKeyException("RSA public keys cannot be used with " + opmodeToString(opmode));
                }
            }
            HwKeyCharacteristics keyCharacteristics = new HwKeyCharacteristics();
            int errorCode = getKeyStore().getKeyCharacteristics(keystoreKey.getAlias(), null, null, keystoreKey.getUid(), keyCharacteristics);
            if (errorCode == 1) {
                long keySizeBits = keyCharacteristics.getUnsignedInt(HwKeymasterDefs.KM_TAG_KEY_SIZE, -1);
                if (keySizeBits == -1) {
                    throw new InvalidKeyException("Size of key not known");
                } else if (keySizeBits <= 2147483647L) {
                    this.mModulusSizeBytes = (int) ((7 + keySizeBits) / 8);
                    setKey(keystoreKey);
                } else {
                    throw new InvalidKeyException("Key too large: " + keySizeBits + " bits");
                }
            } else {
                throw getKeyStore().getInvalidKeyException(keystoreKey.getAlias(), keystoreKey.getUid(), errorCode);
            }
        } else {
            throw new InvalidKeyException("Unsupported key algorithm: " + key.getAlgorithm() + ". Only " + HwKeyProperties.KEY_ALGORITHM_RSA + " supported");
        }
    }

    private void init(int opmode, Key key, SecureRandom random) throws InvalidKeyException {
        switch (opmode) {
            case 1:
            case 3:
                this.mEncrypting = true;
                break;
            case 2:
            case 4:
                this.mEncrypting = false;
                break;
            default:
                throw new InvalidParameterException("Unsupported opmode: " + opmode);
        }
        initKey(opmode, key);
        if (this.mKey != null) {
            this.mRng = random;
            return;
        }
        throw new ProviderException("initKey did not initialize the key");
    }

    /* access modifiers changed from: protected */
    public void initAlgorithmSpecificParameters() throws InvalidKeyException {
    }

    /* access modifiers changed from: protected */
    public void initAlgorithmSpecificParameters(@Nullable AlgorithmParameterSpec params) throws InvalidAlgorithmParameterException {
    }

    /* access modifiers changed from: protected */
    public void initAlgorithmSpecificParameters(@Nullable AlgorithmParameters params) throws InvalidAlgorithmParameterException {
    }

    /* access modifiers changed from: protected */
    public void engineInit(int opmode, Key key, SecureRandom random) throws InvalidKeyException {
        resetAll();
        try {
            init(opmode, key, random);
            initAlgorithmSpecificParameters();
            ensureKeystoreOperationInitialized();
            if (1 == 0) {
                resetAll();
            }
        } catch (InvalidAlgorithmParameterException e) {
            throw new InvalidKeyException(e);
        } catch (Throwable e2) {
            if (0 == 0) {
                resetAll();
            }
            throw e2;
        }
    }

    /* access modifiers changed from: protected */
    public void engineInit(int opmode, Key key, AlgorithmParameterSpec params, SecureRandom random) throws InvalidKeyException, InvalidAlgorithmParameterException {
        resetAll();
        boolean success = false;
        try {
            init(opmode, key, random);
            initAlgorithmSpecificParameters(params);
            ensureKeystoreOperationInitialized();
            success = true;
        } finally {
            if (!success) {
                resetAll();
            }
        }
    }

    /* access modifiers changed from: protected */
    public void engineInit(int opmode, Key key, AlgorithmParameters params, SecureRandom random) throws InvalidKeyException, InvalidAlgorithmParameterException {
        resetAll();
        boolean success = false;
        try {
            init(opmode, key, random);
            initAlgorithmSpecificParameters(params);
            ensureKeystoreOperationInitialized();
            success = true;
        } finally {
            if (!success) {
                resetAll();
            }
        }
    }

    /* access modifiers changed from: protected */
    public final byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
        byte[] output = new byte[0];
        if (this.mCachedException != null) {
            return output;
        }
        try {
            ensureKeystoreOperationInitialized();
            if (inputLen == 0) {
                return output;
            }
            try {
                flushAAD();
                return this.mMainDataStreamer.update(input, inputOffset, inputLen);
            } catch (HwUniversalKeyStoreException e) {
                this.mCachedException = e;
                return new byte[0];
            }
        } catch (InvalidAlgorithmParameterException | InvalidKeyException e2) {
            this.mCachedException = e2;
            return output;
        }
    }

    private void flushAAD() throws HwUniversalKeyStoreException {
        if (this.mAdditionalAuthenticationDataStreamer != null && !this.mAdditionalAuthenticationDataStreamerClosed) {
            try {
                byte[] output = this.mAdditionalAuthenticationDataStreamer.doFinal(ArrayUtils.EmptyArray.BYTE, 0, 0, null, null);
                if (output != null && output.length > 0) {
                    throw new ProviderException("AAD update unexpectedly returned data: " + output.length + " bytes");
                }
            } finally {
                this.mAdditionalAuthenticationDataStreamerClosed = true;
            }
        }
    }

    /* access modifiers changed from: protected */
    public final int engineUpdate(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) throws ShortBufferException {
        byte[] outputCopy = engineUpdate(input, inputOffset, inputLen);
        if (outputCopy == null || outputCopy.length == 0) {
            return 0;
        }
        int outputAvailable = output.length - outputOffset;
        if (outputCopy.length <= outputAvailable) {
            System.arraycopy(outputCopy, 0, output, outputOffset, outputCopy.length);
            return outputCopy.length;
        }
        throw new ShortBufferException("Output buffer too short. Produced: " + outputCopy.length + ", available: " + outputAvailable);
    }

    /* access modifiers changed from: protected */
    public final void engineUpdateAAD(byte[] input, int inputOffset, int inputLen) {
        if (this.mCachedException == null) {
            try {
                ensureKeystoreOperationInitialized();
                if (this.mAdditionalAuthenticationDataStreamerClosed) {
                    throw new IllegalStateException("AAD can only be provided before Cipher.update is invoked");
                } else if (this.mAdditionalAuthenticationDataStreamer != null) {
                    try {
                        byte[] output = this.mAdditionalAuthenticationDataStreamer.update(input, inputOffset, inputLen);
                        if (output != null && output.length > 0) {
                            throw new ProviderException("AAD update unexpectedly produced output: " + output.length + " bytes");
                        }
                    } catch (HwUniversalKeyStoreException e) {
                        this.mCachedException = e;
                    }
                } else {
                    throw new IllegalStateException("This cipher does not support AAD");
                }
            } catch (InvalidAlgorithmParameterException | InvalidKeyException e2) {
                this.mCachedException = e2;
            }
        }
    }

    /* access modifiers changed from: protected */
    public final int engineUpdate(ByteBuffer input, ByteBuffer output) throws ShortBufferException {
        byte[] inputArray;
        if (input == null) {
            throw new NullPointerException("input == null");
        } else if (output != null) {
            int inputSize = input.remaining();
            int outputSize = 0;
            if (input.hasArray()) {
                inputArray = engineUpdate(input.array(), input.arrayOffset() + input.position(), inputSize);
                input.position(input.position() + inputSize);
            } else {
                byte[] inputArray2 = new byte[inputSize];
                input.get(inputArray2);
                inputArray = engineUpdate(inputArray2, 0, inputSize);
            }
            if (inputArray != null && inputArray.length > 0) {
                outputSize = inputArray.length;
            }
            if (outputSize > 0) {
                int outputBufferAvailable = output.remaining();
                try {
                    output.put(inputArray);
                } catch (BufferOverflowException e) {
                    throw new ShortBufferException("Output buffer too small. Produced: " + outputSize + ", available: " + outputBufferAvailable);
                }
            }
            return outputSize;
        } else {
            throw new NullPointerException("output == null");
        }
    }

    /* access modifiers changed from: protected */
    public final void engineUpdateAAD(ByteBuffer src) {
        int inputLen;
        int inputOffset;
        byte[] input;
        if (src == null) {
            throw new IllegalArgumentException("src == null");
        } else if (src.hasRemaining()) {
            if (src.hasArray()) {
                input = src.array();
                inputOffset = src.arrayOffset() + src.position();
                inputLen = src.remaining();
                src.position(src.limit());
            } else {
                input = new byte[src.remaining()];
                inputOffset = 0;
                inputLen = input.length;
                src.get(input);
            }
            engineUpdateAAD(input, inputOffset, inputLen);
        }
    }

    /* access modifiers changed from: protected */
    public final byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen) throws IllegalBlockSizeException, BadPaddingException {
        if (this.mCachedException == null) {
            try {
                ensureKeystoreOperationInitialized();
                try {
                    flushAAD();
                    byte[] output = this.mMainDataStreamer.doFinal(input, inputOffset, inputLen, null, HwUniversalKeyStoreCryptoOperationUtils.getRandomBytesToMixIntoKeystoreRng(this.mRng, getAdditionalEntropyAmountForFinish()));
                    resetWhilePreservingInitState();
                    return output;
                } catch (HwUniversalKeyStoreException e) {
                    int errorCode = e.getErrorCode();
                    if (errorCode == -38) {
                        throw ((BadPaddingException) new BadPaddingException().initCause(e));
                    } else if (errorCode == -30) {
                        throw ((AEADBadTagException) new AEADBadTagException().initCause(e));
                    } else if (errorCode != -21) {
                        throw ((IllegalBlockSizeException) new IllegalBlockSizeException().initCause(e));
                    } else {
                        throw ((IllegalBlockSizeException) new IllegalBlockSizeException().initCause(e));
                    }
                }
            } catch (InvalidAlgorithmParameterException | InvalidKeyException e2) {
                throw ((IllegalBlockSizeException) new IllegalBlockSizeException().initCause(e2));
            }
        } else {
            throw ((IllegalBlockSizeException) new IllegalBlockSizeException().initCause(this.mCachedException));
        }
    }

    /* access modifiers changed from: protected */
    public int getAdditionalEntropyAmountForFinish() {
        return 0;
    }

    /* access modifiers changed from: protected */
    public final int engineDoFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        byte[] outputCopy = engineDoFinal(input, inputOffset, inputLen);
        if (outputCopy == null) {
            return 0;
        }
        int outputAvailable = output.length - outputOffset;
        if (outputCopy.length <= outputAvailable) {
            System.arraycopy(outputCopy, 0, output, outputOffset, outputCopy.length);
            return outputCopy.length;
        }
        throw new ShortBufferException("Output buffer too short. Produced: " + outputCopy.length + ", available: " + outputAvailable);
    }

    /* access modifiers changed from: protected */
    public final int engineDoFinal(ByteBuffer input, ByteBuffer output) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        byte[] inputArray;
        if (input == null) {
            throw new NullPointerException("input == null");
        } else if (output != null) {
            int inputSize = input.remaining();
            int outputSize = 0;
            if (input.hasArray()) {
                inputArray = engineDoFinal(input.array(), input.arrayOffset() + input.position(), inputSize);
                input.position(input.position() + inputSize);
            } else {
                byte[] inputArray2 = new byte[inputSize];
                input.get(inputArray2);
                inputArray = engineDoFinal(inputArray2, 0, inputSize);
            }
            if (inputArray != null) {
                outputSize = inputArray.length;
            }
            if (outputSize > 0) {
                int outputBufferAvailable = output.remaining();
                try {
                    output.put(inputArray);
                } catch (BufferOverflowException e) {
                    throw new ShortBufferException("Output buffer too small. Produced: " + outputSize + ", available: " + outputBufferAvailable);
                }
            }
            return outputSize;
        } else {
            throw new NullPointerException("output == null");
        }
    }

    /* access modifiers changed from: protected */
    public final byte[] engineWrap(Key key) throws IllegalBlockSizeException, InvalidKeyException {
        if (this.mKey == null) {
            throw new IllegalStateException("Not initilized");
        } else if (!isEncrypting()) {
            throw new IllegalStateException("Cipher must be initialized in Cipher.WRAP_MODE to wrap keys");
        } else if (key != null) {
            byte[] encoded = null;
            if (key instanceof SecretKey) {
                if ("RAW".equalsIgnoreCase(key.getFormat())) {
                    encoded = key.getEncoded();
                }
                if (encoded == null) {
                    try {
                        encoded = ((SecretKeySpec) SecretKeyFactory.getInstance(key.getAlgorithm()).getKeySpec((SecretKey) key, SecretKeySpec.class)).getEncoded();
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                        throw new InvalidKeyException("Failed to wrap key because it does not export its key material", e);
                    }
                }
            } else if (key instanceof PrivateKey) {
                if ("PKCS8".equalsIgnoreCase(key.getFormat())) {
                    encoded = key.getEncoded();
                }
                if (encoded == null) {
                    try {
                        encoded = ((PKCS8EncodedKeySpec) KeyFactory.getInstance(key.getAlgorithm()).getKeySpec(key, PKCS8EncodedKeySpec.class)).getEncoded();
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException e2) {
                        throw new InvalidKeyException("Failed to wrap key because it does not export its key material", e2);
                    }
                }
            } else if (key instanceof PublicKey) {
                if ("X.509".equalsIgnoreCase(key.getFormat())) {
                    encoded = key.getEncoded();
                }
                if (encoded == null) {
                    try {
                        encoded = ((X509EncodedKeySpec) KeyFactory.getInstance(key.getAlgorithm()).getKeySpec(key, X509EncodedKeySpec.class)).getEncoded();
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException e3) {
                        throw new InvalidKeyException("Failed to wrap key because it does not export its key material", e3);
                    }
                }
            } else {
                throw new InvalidKeyException("Unsupported key type: " + key.getClass().getName());
            }
            if (encoded != null) {
                try {
                    return engineDoFinal(encoded, 0, encoded.length);
                } catch (BadPaddingException e4) {
                    throw ((IllegalBlockSizeException) new IllegalBlockSizeException().initCause(e4));
                }
            } else {
                throw new InvalidKeyException("Failed to wrap key because it does not export its key material");
            }
        } else {
            throw new NullPointerException("key == null");
        }
    }

    /* access modifiers changed from: protected */
    public final Key engineUnwrap(byte[] wrappedKey, String wrappedKeyAlgorithm, int wrappedKeyType) throws InvalidKeyException, NoSuchAlgorithmException {
        if (this.mKey == null) {
            throw new IllegalStateException("Not initilized");
        } else if (isEncrypting()) {
            throw new IllegalStateException("Cipher must be initialized in Cipher.WRAP_MODE to wrap keys");
        } else if (wrappedKey != null) {
            try {
                byte[] encoded = engineDoFinal(wrappedKey, 0, wrappedKey.length);
                switch (wrappedKeyType) {
                    case 1:
                        try {
                            return KeyFactory.getInstance(wrappedKeyAlgorithm).generatePublic(new X509EncodedKeySpec(encoded));
                        } catch (InvalidKeySpecException e) {
                            throw new InvalidKeyException("Failed to create public key from its X.509 encoded form", e);
                        }
                    case 2:
                        try {
                            return KeyFactory.getInstance(wrappedKeyAlgorithm).generatePrivate(new PKCS8EncodedKeySpec(encoded));
                        } catch (InvalidKeySpecException e2) {
                            throw new InvalidKeyException("Failed to create private key from its PKCS#8 encoded form", e2);
                        }
                    case 3:
                        return new SecretKeySpec(encoded, wrappedKeyAlgorithm);
                    default:
                        throw new InvalidParameterException("Unsupported wrappedKeyType: " + wrappedKeyType);
                }
            } catch (BadPaddingException | IllegalBlockSizeException e3) {
                throw new InvalidKeyException("Failed to unwrap key", e3);
            }
        } else {
            throw new NullPointerException("wrappedKey == null");
        }
    }

    /* access modifiers changed from: protected */
    public final void setKeymasterPurposeOverride(int keymasterPurpose) {
        this.mKeymasterPurposeOverride = keymasterPurpose;
    }

    /* access modifiers changed from: protected */
    public final int getKeymasterPurposeOverride() {
        return this.mKeymasterPurposeOverride;
    }

    public final int getKeymasterPaddingOverride() {
        return this.mKeymasterPaddingOverride;
    }

    public final void setKeymasterPaddingOverride(int mKeymasterPaddingOverride2) {
        this.mKeymasterPaddingOverride = mKeymasterPaddingOverride2;
    }

    /* access modifiers changed from: protected */
    public final boolean isEncrypting() {
        return this.mEncrypting;
    }

    public final long getOperationHandle() {
        return this.mOperationHandle;
    }

    /* access modifiers changed from: protected */
    public final void setKey(@NonNull HwUniversalKeyStoreKey key) {
        this.mKey = key;
    }

    /* access modifiers changed from: protected */
    public final HwUniversalKeyStoreKey getKey() {
        return this.mKey;
    }

    /* access modifiers changed from: protected */
    @NonNull
    public final HwKeystoreManager getKeyStore() {
        return this.mKeyStore;
    }

    /* access modifiers changed from: protected */
    public final long getConsumedInputSizeBytes() {
        if (this.mMainDataStreamer != null) {
            return this.mMainDataStreamer.getConsumedInputSizeBytes();
        }
        throw new IllegalStateException("Not initialized");
    }

    /* access modifiers changed from: protected */
    public final long getProducedOutputSizeBytes() {
        if (this.mMainDataStreamer != null) {
            return this.mMainDataStreamer.getProducedOutputSizeBytes();
        }
        throw new IllegalStateException("Not initialized");
    }

    /* access modifiers changed from: protected */
    @CallSuper
    public void finalize() throws Throwable {
        try {
            IBinder operationToken = this.mOperationToken;
            if (operationToken != null) {
                this.mKeyStore.abort(operationToken);
            }
        } finally {
            super.finalize();
        }
    }

    static String opmodeToString(int opmode) {
        switch (opmode) {
            case 1:
                return "ENCRYPT_MODE";
            case 2:
                return "DECRYPT_MODE";
            case 3:
                return "WRAP_MODE";
            case 4:
                return "UNWRAP_MODE";
            default:
                return String.valueOf(opmode);
        }
    }
}
