package com.shuttermute.privilege

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

class LocalAdbManager private constructor(context: Context) : AbsAdbConnectionManager() {

    private val privateKey: PrivateKey
    private val certificate: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        val identity = loadOrCreate(context.applicationContext)
        privateKey = identity.first
        certificate = identity.second
    }

    override fun getPrivateKey(): PrivateKey = privateKey

    override fun getCertificate(): Certificate = certificate

    override fun getDeviceName(): String = "ShutterMute"

    companion object {
        @Volatile
        private var instance: LocalAdbManager? = null

        fun get(context: Context): LocalAdbManager {
            return instance ?: synchronized(this) {
                instance ?: LocalAdbManager(context.applicationContext).also { instance = it }
            }
        }

        private fun loadOrCreate(context: Context): Pair<PrivateKey, X509Certificate> {
            val dir = File(context.filesDir, "adb")
            dir.mkdirs()
            val keyFile = File(dir, "adbkey")
            val certFile = File(dir, "adbkey.crt")
            if (keyFile.exists() && certFile.exists()) {
                val key = KeyFactory.getInstance("RSA")
                    .generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(certFile.inputStream()) as X509Certificate
                return key to cert
            }
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048, SecureRandom())
            val keyPair = generator.generateKeyPair()
            val cert = selfSigned(keyPair.private, keyPair.public)
            keyFile.writeBytes(keyPair.private.encoded)
            certFile.writeBytes(cert.encoded)
            return keyPair.private to cert
        }

        private fun selfSigned(privateKey: PrivateKey, publicKey: java.security.PublicKey): X509Certificate {
            val now = System.currentTimeMillis()
            val name = X500Name("CN=ShutterMute")
            val builder = JcaX509v3CertificateBuilder(
                name,
                BigInteger(64, SecureRandom()),
                Date(now - 86_400_000L),
                Date(now + 10L * 365 * 24 * 60 * 60 * 1000),
                name,
                publicKey,
            )
            val signer = JcaContentSignerBuilder("SHA256WithRSAEncryption").build(privateKey)
            return JcaX509CertificateConverter().getCertificate(builder.build(signer))
        }
    }
}
