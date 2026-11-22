package com.mubarak.bydspeed.car

import android.content.Context
import android.util.Base64
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.PRNGFixes
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.security.auth.x500.X500Principal

/**
 * Holds the app's own ADB RSA identity (persisted) so the head unit shows the "allow debugging"
 * prompt only once. Subclass of libadb-android's connection manager.
 */
class AdbCarManager private constructor(context: Context) : AbsAdbConnectionManager() {
    private val privateKey: PrivateKey
    private val certificate: Certificate

    init {
        setApi(android.os.Build.VERSION.SDK_INT)
        setTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        setThrowOnUnauthorised(true)
        val keyFile = File(context.filesDir, "adb_key")
        val certFile = File(context.filesDir, "adb_cert.pem")
        if (keyFile.exists() && certFile.exists()) {
            privateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(certFile.inputStream())
        } else {
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            privateKey = kp.private
            certificate = generateCertificate(kp.public, kp.private)
            keyFile.writeBytes(privateKey.encoded)
            certFile.writeText(
                "-----BEGIN CERTIFICATE-----\n" +
                    Base64.encodeToString(certificate.encoded, Base64.NO_WRAP).chunked(64).joinToString("\n") +
                    "\n-----END CERTIFICATE-----\n"
            )
        }
    }

    override fun getPrivateKey(): PrivateKey = privateKey
    override fun getCertificate(): Certificate = certificate
    override fun getDeviceName(): String = "BydCarHub"

    private fun generateCertificate(pub: PublicKey, priv: PrivateKey): Certificate {
        // Minimal self-signed X.509 via BouncyCastle (bundled as a dependency).
        val now = System.currentTimeMillis()
        val notBefore = java.util.Date(now - 86_400_000L)
        val notAfter = java.util.Date(now + 3L * 365 * 86_400_000L)
        val serial = java.math.BigInteger.valueOf(now)
        val subject = X500Principal("CN=BydCarHub")
        val builder = org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject, pub
        )
        val signer = org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
            .build(priv)
        return org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
            .setProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
            .getCertificate(builder.build(signer))
    }

    companion object {
        @Volatile private var instance: AdbCarManager? = null

        fun getInstance(context: Context): AdbCarManager {
            return instance ?: synchronized(this) {
                instance ?: AdbCarManager(context.applicationContext).also {
                    runCatching { PRNGFixes.apply() }
                    instance = it
                }
            }
        }
    }
}
