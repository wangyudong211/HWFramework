package com.android.org.bouncycastle.asn1.x9;

import com.android.org.bouncycastle.asn1.ASN1ObjectIdentifier;
import com.android.org.bouncycastle.util.Strings;
import java.util.Enumeration;
import java.util.Hashtable;

public class X962NamedCurves {
    static X9ECParametersHolder c2pnb163v1;
    static X9ECParametersHolder c2pnb163v2;
    static X9ECParametersHolder c2pnb163v3;
    static X9ECParametersHolder c2pnb176w1;
    static X9ECParametersHolder c2pnb208w1;
    static X9ECParametersHolder c2pnb272w1;
    static X9ECParametersHolder c2pnb304w1;
    static X9ECParametersHolder c2pnb368w1;
    static X9ECParametersHolder c2tnb191v1;
    static X9ECParametersHolder c2tnb191v2;
    static X9ECParametersHolder c2tnb191v3;
    static X9ECParametersHolder c2tnb239v1;
    static X9ECParametersHolder c2tnb239v2;
    static X9ECParametersHolder c2tnb239v3;
    static X9ECParametersHolder c2tnb359v1;
    static X9ECParametersHolder c2tnb431r1;
    static final Hashtable curves = null;
    static final Hashtable names = null;
    static final Hashtable objIds = null;
    static X9ECParametersHolder prime192v1;
    static X9ECParametersHolder prime192v2;
    static X9ECParametersHolder prime192v3;
    static X9ECParametersHolder prime239v1;
    static X9ECParametersHolder prime239v2;
    static X9ECParametersHolder prime239v3;
    static X9ECParametersHolder prime256v1;

    static {
        /* JADX: method processing error */
/*
        Error: jadx.core.utils.exceptions.DecodeException: Load method exception in method: com.android.org.bouncycastle.asn1.x9.X962NamedCurves.<clinit>():void
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:113)
	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:256)
	at jadx.core.ProcessClass.process(ProcessClass.java:34)
	at jadx.core.ProcessClass.processDependencies(ProcessClass.java:59)
	at jadx.core.ProcessClass.process(ProcessClass.java:42)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:281)
	at jadx.api.JavaClass.decompile(JavaClass.java:59)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:161)
Caused by: jadx.core.utils.exceptions.DecodeException:  in method: com.android.org.bouncycastle.asn1.x9.X962NamedCurves.<clinit>():void
	at jadx.core.dex.instructions.InsnDecoder.decodeInsns(InsnDecoder.java:46)
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:98)
	... 7 more
Caused by: java.lang.IllegalArgumentException: bogus opcode: 0073
	at com.android.dx.io.OpcodeInfo.get(OpcodeInfo.java:1197)
	at com.android.dx.io.OpcodeInfo.getFormat(OpcodeInfo.java:1212)
	at com.android.dx.io.instructions.DecodedInstruction.decode(DecodedInstruction.java:72)
	at jadx.core.dex.instructions.InsnDecoder.decodeInsns(InsnDecoder.java:43)
	... 8 more
*/
        /*
        // Can't load method instructions.
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.org.bouncycastle.asn1.x9.X962NamedCurves.<clinit>():void");
    }

    static void defineCurve(String name, ASN1ObjectIdentifier oid, X9ECParametersHolder holder) {
        objIds.put(name.toLowerCase(), oid);
        names.put(oid, name);
        curves.put(oid, holder);
    }

    public static X9ECParameters getByName(String name) {
        ASN1ObjectIdentifier oid = getOID(name);
        if (oid == null) {
            return null;
        }
        return getByOID(oid);
    }

    public static X9ECParameters getByOID(ASN1ObjectIdentifier oid) {
        X9ECParametersHolder holder = (X9ECParametersHolder) curves.get(oid);
        if (holder == null) {
            return null;
        }
        return holder.getParameters();
    }

    public static ASN1ObjectIdentifier getOID(String name) {
        return (ASN1ObjectIdentifier) objIds.get(Strings.toLowerCase(name));
    }

    public static String getName(ASN1ObjectIdentifier oid) {
        return (String) names.get(oid);
    }

    public static Enumeration getNames() {
        return names.elements();
    }
}
